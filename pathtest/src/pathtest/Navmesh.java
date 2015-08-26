package pathtest;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import pathtest.util.HashSet;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.IllegalArgumentException;
import java.lang.reflect.Array;
import java.time.chrono.MinguoChronology;



public class Navmesh extends java.util.Observable {

	public static final double NODE_SNAP_DIST = 0.5;
	
	
	public class Point extends Point2D.Double {
		private static final long serialVersionUID = 1L;
		
		public Point(double x, double y) {
			super(x,y);
		}
		
	}
	

	public List<Cell> aStar(Cell start, Point target) {
		
		class aStarCell implements Comparable<aStarCell> {
			
			public double knownCost;
			public final Cell cell;
			public aStarCell parent;
			
			public aStarCell(Cell cell) {
				this.cell = cell;
			}
			
			public double totalCost() {
				return knownCost+costToTarget();
			}
			
			public double costToTarget() {
				return this.costTo(target);
			}
			
			public double costTo(Cell other ) {
				return this.costTo(other.centre);
			}
			
			public double costTo(Point target) {
				//return Math.abs(target.x - this.cell.centre.x) + Math.abs(target.y - this.cell.centre.y);
				return this.cell.centre.distance(target);
			}
			
			public int compareTo(aStarCell other) {
				double diff = this.totalCost() - other.totalCost();
				return (int) Math.signum(diff);
			}
			
			
		}
		
		{
			Map<String, Object> notification = new HashMap<String, Object>();
			notification.put("startCell", start);
			notification.put("endPoint", target);
			setChanged();
			notifyObservers(notification);
		}
		
		PriorityQueue<aStarCell> cellsToCheck = new PriorityQueue<aStarCell>();
		LinkedList<aStarCell> exploredPath;
		LinkedList<Point[]> exploredPathDebug;
		ReentrantLock explorationLock = new ReentrantLock();
		
		Runnable debugCheckList = new Runnable() {
			@Override public void run() {
				for (aStarCell cell: cellsToCheck) {
					System.out.printf("  Cell (%f, %f): %.1f + %.1f = %.1f\n",
							cell.cell.centre.x,
							cell.cell.centre.y,
							cell.knownCost,
							cell.costToTarget(),
							cell.totalCost());
				}
			}
		};
		
		exploredPath = new LinkedList<aStarCell>();
		exploredPathDebug = new LinkedList<Point[]>();
		cellsToCheck.add(new aStarCell(start));
		Cell targetCell = this.getCellContaining(target);
		
		while (cellsToCheck.peek().cell != targetCell) {
			debugCheckList.run();
			aStarCell currentCell = cellsToCheck.poll();
			
			exploredPath.addLast(currentCell);
			
			if (currentCell.parent != null) {
				explorationLock.lock();
				try {
					exploredPathDebug.add(new Point[] {
							currentCell.parent.cell.centre,
							currentCell.cell.centre
					});
				} finally {
					explorationLock.unlock();
				}
			}
			
			setChanged();
			{
				HashMap<String, Object> notification = new HashMap<String, Object>();
				notification.put("exploredPath", exploredPathDebug);
				notification.put("explorationLock", explorationLock);
				notifyObservers(notification);
			}
			System.out.printf("added (%f, %f) to the path\n", currentCell.cell.centre.x, currentCell.cell.centre.y);
			for (Cell neighbour : currentCell.cell.neighbours()) {
				double knownCostForNeighbour = currentCell.knownCost
											   + currentCell.costTo(neighbour);
				
				boolean inCellsToCheck = false;
				
				Optional<aStarCell> alreadyChecking = cellsToCheck.stream().filter(c -> c.cell == neighbour).findAny();
				if (alreadyChecking.isPresent()) {
					inCellsToCheck = true;
					System.out.printf("we are already checking (%f, %f)\n", neighbour.centre.x, neighbour.centre.y);
					if (alreadyChecking.get().knownCost > knownCostForNeighbour) {
						inCellsToCheck = false;
						cellsToCheck.remove(alreadyChecking.get());
						System.out.printf("found a better path to (%f, %f)\n", neighbour.centre.x, neighbour.centre.y);
					}
				}
				
				boolean inKnownPath = false;
				
				Optional<aStarCell> alreadyKnown = exploredPath.stream().filter(c -> c.cell == neighbour).findAny();
				if (alreadyKnown.isPresent()) {
					inKnownPath = true;
					System.out.printf("we have already added (%f, %f)\n", neighbour.centre.x, neighbour.centre.y);
					if (alreadyKnown.get().knownCost > knownCostForNeighbour) {
						inKnownPath = true;
						exploredPath.remove(alreadyKnown.get());
						System.out.printf("removed an existing node, this should never happen\n");
					}
				}
				
				if (! (inKnownPath || inCellsToCheck)) {
					aStarCell neighbourToCheck = new aStarCell(neighbour);
					neighbourToCheck.knownCost = knownCostForNeighbour;
					neighbourToCheck.parent = currentCell;
					cellsToCheck.add(neighbourToCheck);
					System.out.printf("added (%f, %f) to cells to check\n", neighbour.centre.x, neighbour.centre.y);
				}
			}
		}
		
		LinkedList<Cell> knownPath = new LinkedList<Cell>();
		aStarCell knownCell = exploredPath.getLast();
		do {
			knownPath.addFirst(knownCell.cell);
			knownCell = knownCell.parent;
		} while (knownCell.cell!=start);
		return knownPath;
	}
	
	//possible gotcha: my edges are undirectional, i.e. p1 -> p2 == p2 -> 1.
	public class Edge {
	    final Point node1;
	    final Point node2;

	    private Set<Cell> cells;
	    
	    public Edge(Point node1, Point node2) {
	    	this.node1 = node1;
	    	this.node2 = node2;
	    	this.cells = new HashSet<Cell>();
	    }
	    
	    public void connectCell(Cell newCell) {
	    	this.cells.add(newCell);
	    }
	    
	    @Override
	    public boolean equals(Object o) {
	        if (o == null) return false;
	        if (!(o instanceof Edge)) return false;
	        Edge otherEdge = (Edge)o;
	        return (
	        	   (otherEdge.firstNode().equals(this.firstNode()))
	        	&& (otherEdge.secondNode().equals(this.secondNode()))
	        );
	    }
	    
	    @Override
	    public int hashCode() {
	    	return ((this.firstNode().hashCode() >> 16) << 16) | (this.secondNode().hashCode() >> 16);
	    }
	    
	    public Point firstNode() {
	    	if (node1.hashCode() <= node2.hashCode()) {
	    		return node1;
	    	} else {
	    		return node2;
	    	}
	    }
	    
	    public Point secondNode() {
	    	if (node1.hashCode() <= node2.hashCode()) {
	    		return node2;
	    	} else {
	    		return node1;
	    	}
	    }
	    
	    
	}
	
	// a cell. Must be a convex polygon.
	//TODO: actually enforce convexity
	//TODO: deleting cells will require some cleanup
	public class Cell {
	    private final Edge[] edges;
	    private final Point[] points;
	    private final double[][] edgeEquations;
	    public final Point centre;
	    private Cell(Edge[] edges, Point[] nodes) {
	    	
	    	this.edges = edges.clone();
	    	this.points = nodes.clone();
	    	for (Edge edge: edges) {
	    		// we want to be able to easily ask "what cells does this edge touch?"
	    		edge.connectCell(this);
	    	}

	    	centre = new Point(
	    				Arrays.stream(nodes).mapToDouble(p->p.x).average().getAsDouble(),
	    				Arrays.stream(nodes).mapToDouble(p->p.y).average().getAsDouble()
    				 );					
	    	
	    	this.edgeEquations = new double[this.points.length][3];
	    	for (int i = 0; i<this.points.length; i++) {
	    		Point p1 = this.points[i];
	    		Point p2 = this.points[(i+1) % this.points.length];
	    		
	    		// eqn for a line:
	    		// | x  y  1 |
	    		// | x1 y1 1 | = 0
	    		// | x2 y2 1 |
	    		//
	    		//   | y1 1 |     | x1 1 |     | x1 y1 |
	    		// x | y2 1 | - y | x2 1 | + 1 | x2 y2 | = 0
	    		//
	    		// so fitting the form ax+by+c = 0,
	    		//
	    		//      | y1 1 |       | x1 1 |      | x1 y1 |
	    		// a =  | y2 1 |, b = -| x2 1 |, c = | x2 y2 |
	    		
	    		double a = (p1.y)-(p2.y);
	    		double b = - ( (p1.x)-(p2.x) );
	    		double c = (p1.x*p2.y) - (p2.x*p1.y);
	    		
	    		this.edgeEquations[i][0] = a;
	    		this.edgeEquations[i][1] = b;
	    		this.edgeEquations[i][2] = c;
	    	}
	    }
	    
	    public boolean contains(Point testNode) {
	    	//go around all the nodes IN ORDER. We will build some algebraic level sets for the edges.
	    	//maintaining node order means that these expressions will all evaluate positive/negative
	    	//for points towards the centre/outside of the polygon in the same way.
	    	//We can then just evaluate our testNode in all of these expressions and check if the answers
	    	//are all of the same sign; if so, then our point is in the polygon.
	    	
	    	double testDirection = 0;
	    	for (int i = 0; i < this.points.length; i++) {
	    		//evaluate ax+by+c
	    		double testEvaluation =   edgeEquations[i][0]*testNode.x
	    				                + edgeEquations[i][1]*testNode.y
	    				                + edgeEquations[i][2];
	    		
	    		if (testEvaluation == 0) {
	    			// we are actually on an edge (technically the infinite extention of that edge),
	    			// if you ever played taps you would know that on the line counts as in?
	    			continue;
	    		} else if (testDirection == 0) {
	    			// must be the first time through
	    			testDirection = testEvaluation;
	    		} else if (testDirection * testEvaluation < 0) {
    				// if this direction and the first direction are of different sign
    				return false;
    			} else {
    				// no problems! check the next one.
    				continue;
    			}
	    		
	    	}
    		return true;
	    }
	    
	    public Collection<Cell> neighbours() {
	    	Collection<Cell> neighbours = new HashSet<Cell>();
	    	for (Edge edge: this.edges) {
	    		neighbours.addAll(edge.cells);
	    	}
	    	neighbours.remove(this);
	    	return neighbours;
	    }
	    
	    public double[] getXpoints() {
	    	return Arrays.stream(this.points).mapToDouble(p -> p.x).toArray();
	    }
	    
	    public double[] getYpoints() {
	    	return Arrays.stream(this.points).mapToDouble(p -> p.y).toArray();
	    }
	    
	    public int getPointsCount() {
	    	return this.points.length;
	    }
	}
	
	private HashSet<Point> nodes;
	private HashSet<Edge> edges;
	private HashSet<Cell> cells;
	
	protected Cell addCell(double... nodePoints) {
		
		if (nodePoints.length % 2 != 0) {
			throw new IllegalArgumentException("addCell needs as list of coordinates x,y,x,y,etc. It was provided with a list of odd size,");
		}
		
		Point[] cellNodes = new Point[nodePoints.length/2];
		
		for (int i=0; i<nodePoints.length; i+=2) {
			
			double x = nodePoints[i];
			double y = nodePoints[i+1];

			Point nodeToAdd = null;
			for (Point testNode : this.nodes) {
				if (testNode.distanceSq(x,y)  < NODE_SNAP_DIST) {
					nodeToAdd = testNode;
					break;
				}
			}
			if (nodeToAdd == null) {
				nodeToAdd = new Point(x, y);
				this.nodes.add(nodeToAdd);
			}
			cellNodes[i/2] = nodeToAdd;
			
		}
		
		Edge[] cellEdges = new Edge[cellNodes.length];
		
		for (int i = 0; i< cellNodes.length; i++) {
			Edge newEdge = new Edge(cellNodes[i], cellNodes[(i+1) % cellNodes.length]);
			cellEdges[i] = this.edges.getOrAdd(newEdge);
		}
		
		Cell newCell = new Cell(cellEdges, cellNodes);
		this.cells.add(newCell);
		this.setChanged();
		this.notifyObservers();
		return newCell;
	}
	
	public Cell getCellContaining(Point point) {
		return this.cells.stream()
						 .filter(c -> c.contains(point))
						 .findAny()
						 .get();
	}
	
	public Cell[] getCells() {
		Cell[] cellsArray = new Cell[this.cells.size()];
		this.cells.toArray(cellsArray);
		return cellsArray;
	}
	
	public Navmesh() {
		this.nodes = new HashSet<Point>();
		this.edges = new HashSet<Edge>();
		this.cells = new HashSet<Cell>();
		notifyObservers();
	}
	
	public static Navmesh fromFile(String filename) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(filename));
		String line;
		Navmesh n = new Navmesh();
		while ((line = reader.readLine()) != null) {
			String[] nodeStrings = line.split(";");
			double[] coords = new double[nodeStrings.length*2];
			for (int i = 0; i<nodeStrings.length; i++) {
				String[] splitCoord = nodeStrings[i].split(",");
				coords[i*2] = Double.parseDouble(splitCoord[0]);
				coords[i*2+1] = Double.parseDouble(splitCoord[1]);
			}
			n.addCell(coords);
		};
		reader.close();
		return n;
	}
	
	public Rectangle2D getBoundingBox() {
		double bottom = Double.MAX_VALUE;
		double top = Double.MIN_VALUE;
		double left = Double.MAX_VALUE;
		double right = Double.MIN_VALUE;
		for (Point p : this.nodes) {
			bottom = Math.min(p.y, bottom);
			top = Math.max(p.y, top);
			left = Math.min(p.x, left);
			right = Math.max(p.x, right);
		}
		return new Rectangle2D.Double(left, top, right-left, top-bottom);
	}

	

}
