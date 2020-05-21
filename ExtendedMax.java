
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javafx.util.*;

import org.jgraph.graph.DefaultEdge;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultUndirectedGraph;
import org.jgrapht.traverse.DepthFirstIterator;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;

public class ExtendedMax {
	
	static GraphDatabaseService db = null;
	public static void main(String[] args) throws IOException {

		parser("query file path");
	}
	
	public static ArrayList<String> neighboursOfQueryGraph(DefaultUndirectedGraph<String, DefaultEdge> dg, String node) {	
		ArrayList<String> n = new ArrayList<String>();
		
		for(String nodes: Graphs.neighborListOf(dg, node)) {
			n.add(nodes);
		}
		return n;
	}
	 
	public static ArrayList<Integer> neighboursOfDataGraph(String Label, int nodeId) {
		String cypherQuery = "Match (n1:" + Label + ") -- (n2:" + Label + ") where n1.OGID = \""+ nodeId 
				+"\" return distinct n2.OGID, n2.attribute";
		
		ArrayList<Integer> neighbours = new ArrayList<Integer>();
		HashMap<Integer, Integer> neighbour = new HashMap<Integer, Integer>();
		
		Result res = db.execute(cypherQuery);
		Map<String, Object> data = null;
		try (Transaction tx=db.beginTx()) {
			while(res.hasNext()) {
				
				data = new HashMap<>();
				data = res.next(); 
				
				int neighbourId = Integer.parseInt(data.get("n2.OGID").toString()); 
				String attribute = data.get("n2.attribute").toString();
				
				neighbours.add(neighbourId);
				neighbour.put(neighbourId, 1);
				data = null;
			}
			tx.success();
		}
		return neighbours;
	}
	
	public static ArrayList<Integer>[] refineSearchSpace(ArrayList<Integer>[] candidates, ArrayList<String> Q, String Label, DefaultUndirectedGraph<String, DefaultEdge> dg) {
		
		String u = null;
		int v, k;
		ArrayList<Integer> vNeighbours = null;
		ArrayList<String> uNeighbours = null;
		Map<String, ArrayList<Integer>> bipartite0 = null;
		Map<String, ArrayList<Integer>> bipartite1 = null;
		ArrayList<Integer>[] marked = new ArrayList[candidates.length];
		for(int j = 0; j < candidates.length; j++) {
			marked[j] = new ArrayList<Integer>(); 
		}
		
		ArrayList<Integer> al = null;
		
		for(int i = 0; i < Q.size(); i ++) {
			u = Q.get(i);
			k = Integer.parseInt(u.substring(1, u.length()));
			uNeighbours = neighboursOfQueryGraph(dg, u);
			for(int j = 0; j < candidates[ k ].size(); j++) {
				v = candidates[ k ].get(j);
				marked[k].add(v);
			}
		}
		
		
		for(int i = 0; i < Q.size(); i ++) {
			u = Q.get(i);
			k = Integer.parseInt(u.substring(1, u.length()));
			uNeighbours = neighboursOfQueryGraph(dg, u);
			for(int j = 0; j < candidates[ k ].size(); j++) {
				v = candidates[ k ].get(j);
 				if( Mark(u, v) ) { 
					vNeighbours = neighboursOfDataGraph(Label, v);
					
					bipartite0 = new HashMap<String, ArrayList<Integer>>();
					bipartite1 = new HashMap<String, ArrayList<Integer>>();
					
//					Bipartite
					for(String unode: uNeighbours) {
						for(int vnode: vNeighbours) {
							int uid = Integer.parseInt(unode.substring(1, unode.length()));
							if(candidates[uid].contains(vnode)) {
								
								if(!bipartite1.containsKey(unode)) {
									bipartite1.put(unode, new ArrayList<Integer>());
								}
								al = bipartite1.get(unode);
								if(!al.contains(vnode))
									al.add(vnode);
								bipartite1.put(unode, al);
							}
							else {
								
								if(!bipartite0.containsKey(unode)) {
									bipartite0.put(unode, new ArrayList<Integer>());
								}
								al = bipartite0.get(unode);
								if(!al.contains(vnode))
									al.add(vnode);
								bipartite0.put(unode, al);
							}
						}
					}
					
					if(bipartite1.size() < uNeighbours.size()) {
						candidates[k].remove(new Integer(v));
						j--;
						continue;
					}
					else {
						
						
						DefaultDirectedGraph<String, DefaultEdge> dg1 = new DefaultDirectedGraph<>(DefaultEdge.class);
						
						for (Map.Entry<String, ArrayList<Integer>> entry : bipartite1.entrySet()) {
							dg1.addVertex(entry.getKey());
							for(int s: entry.getValue()) {
								dg1.addVertex(s+"");
								dg1.addEdge(entry.getKey(), s+"");
							}
						}
						
						if(!containAugmentedPath(dg1, bipartite1, uNeighbours, new HashMap<String, String>(), new ArrayList<String>(),
								new ArrayList<String>())) {
							candidates[k].remove(new Integer(v));
							j--;
							
							for(String unode: uNeighbours) {
								int h = Integer.parseInt(unode.substring(1, unode.length()));
								for(int vnode: vNeighbours) {
									if(!marked[h].contains(new Integer(vnode))) {
										marked[h].add(vnode);
									}	
								}
							}
							
							continue;
						}
						else {
							marked[k].remove(new Integer(v));
						}
					}	 
				}	
			}
		}
		
		return candidates;
	}
	
	
	public static boolean containAugmentedPath( DefaultDirectedGraph<String, DefaultEdge> dg1, Map<String, ArrayList<Integer>> bipartite1,
			ArrayList<String> Qnodes, HashMap<String, String> M, ArrayList<String> Qvisited, ArrayList<String> Dvisited) {

		String qnode = null;
		String node = null;
		DepthFirstIterator<String, DefaultEdge>  iterator = null;
		for(int i = 0; i < Qnodes.size(); i++) {
			
			qnode = Qnodes.get(i);
			
			if(!Qvisited.contains(qnode)) {
				
				iterator = new DepthFirstIterator<>(dg1, qnode+"");
				iterator.next();
				while(iterator.hasNext()) {
					node = iterator.next();  
					
					if(!Dvisited.contains(node) && !Qvisited.contains(node)) {
						Qvisited.add(qnode);
						Dvisited.add(node);
						M.put(qnode, node);
						
						dg1.addEdge(node, qnode);
						break;
					} 			
				}
			}
		}
		
		if(M.size() == Qnodes.size()) {
			return true;
		}
		
		return false;
	}
	
	public static boolean hasFreeDataNode(Map<String, ArrayList<Integer>> bipartite1, ArrayList<String> Qvisited, ArrayList<Integer> Dvisited, 
			String qnode, int dnode) {
		
		for(int dnodes: bipartite1.get(qnode)) {
			if(dnodes != dnode) {
				if(!Dvisited.contains(dnodes)) {
					return true;
				}
			}
		}
		
		return false;
	}
	
	
	public static void parser(String fileURL) throws IOException {
		
		db = new GraphDatabaseFactory()
				.newEmbeddedDatabaseBuilder(new File("data file name"))
				.setConfig(GraphDatabaseSettings.pagecache_memory, "512M" )
				.setConfig(GraphDatabaseSettings.string_block_size, "60" )
				.setConfig(GraphDatabaseSettings.array_block_size, "300" )
				.newGraphDatabase();
		
		File[] files = new File("query file path").listFiles();
		BufferedReader br = null;
		String line = null;
		String row[] = null; 
		String name[] = null; 
		
		String match = null, where = null, Return = null;
		ArrayList<String> Nodes = null;
		String Label  = null, T = null, P = null;
		HashMap<String, String> map1 = null;
		ArrayList<Integer>[] candidates = null; 
		
		int numOfNodes = 0 , index = 0, i = 0;
		for(File file : files) {
			name = file.getName().trim().split("\\.");
			
			if(Integer.valueOf(name[1]) != 8)
				continue; 
			
			for(File targetFiles: new File("target file path").listFiles()) {

				candidates = new ArrayList[Integer.valueOf(name[1])];
				for(int j = 0; j < candidates.length; j++) {
					candidates[j] = new ArrayList<Integer>(); 
				}
				
				DefaultUndirectedGraph<String, DefaultEdge> dg = new DefaultUndirectedGraph<>(DefaultEdge.class);
				
				br = new BufferedReader(new FileReader("query file path"+file.getName()));
				line = "";
				row = null;
				ArrayList<Integer> degrees = new ArrayList<Integer>();
				i = 0;
				numOfNodes = 0;
				br.readLine();
				Label = targetFiles.getName().trim().split("\\.")[0];
				map1 = new HashMap<String, String>();
				
				
				while ((line = br.readLine()) != null) {
					row = line.trim().split(" ");
					int nodeID = 0; 
					
					if(row.length == 1) {
						degrees.add(Integer.valueOf(row[0]));
					} 
				}
				br.close();
								
				br = new BufferedReader(new FileReader("query file path"+file.getName()));
				line = "";
				row = null;
				i = 0;
				String cypherQuery = "", fileLabel = "";
				numOfNodes = Integer.parseInt(br.readLine().trim());
				int neighbours = 0, flag = 0;
				String var = null;
				
				while ((line = br.readLine()) != null) {
					row = line.trim().split(" ");
					int nodeID = 0; 
					
					if(row.length == 2) { 
						if(i < numOfNodes) {
							
							System.out.println(dg.addVertex(row[1]+row[0]));
							if(flag == 0) {
								var = row[1]+row[0];
								flag =1;
							}
							
							neighbours = degrees.get(Integer.valueOf(row[0]));
							map1.put(row[0], row[1]);
							
							cypherQuery = "Match (n:" + Label + ") where n.neighbour >= "+neighbours + " and n.attribute = \""+row[1]
									+"\" return distinct n.OGID";
							
							
							Result res = db.execute(cypherQuery);
							HashMap<String, Object> map = new HashMap<>();;
							String output = null; 
							int c = 0;
							Map<String, Object> properties = null;
							Map<String, Object> data = null; 
							try (Transaction tx=db.beginTx()) {
								while(res.hasNext()) {
									
									data = new HashMap<>();
									data = res.next(); 
											
									map.put(output, "1");
									candidates[Integer.valueOf(row[0])].add(Integer.valueOf(data.get("n.OGID").toString()));
									c++;  
									
									data = null;
									properties = null;
								}
								tx.success();
							}
							catch (Exception e) {
							}
							System.out.println("N:" + c); 
							
							System.out.println(cypherQuery);
						}
						else{
							dg.addEdge(map1.get(row[0])+row[0], map1.get(row[1])+row[1]);
						}
					} 
					i++;
				}
				
				DepthFirstIterator<String, DefaultEdge>  iterator = 
						new DepthFirstIterator<>(dg, var);
				ArrayList<String> Q = new ArrayList<String>();
				ArrayList<String> D = new ArrayList<String>();
				HashMap<String, Integer> phai = new HashMap<String, Integer>();
				while(iterator.hasNext()) {
					String as = iterator.next(); 
					
					Q.add(as);
					System.out.println(as);
				}
				System.out.println();
				
				System.out.println("Initial Candidates:");
				for(int f = 0 ; f < candidates.length; f++ ) {
					System.out.println(candidates[f]);
					System.out.println("size: " + candidates[f].size());
				}
				System.out.println();
				
				System.out.println("Order: "+Q);
				candidates = refineSearchSpace(candidates, Q, Label, dg);
				
				System.out.println();
				System.out.println("Updated Candidates:");
				for(int f = 0 ; f < candidates.length; f++ ) {
					System.out.println(candidates[f]);
					System.out.println("size: " + candidates[f].size());
				}
				
				long t1 = System.currentTimeMillis();
				
				HashMap<String, Integer> M = new HashMap<String, Integer>();
				HashMap<String, Integer> result = new HashMap<String, Integer>();
				HashMap<String, Integer> queryNodes = new HashMap<String, Integer>();
				ArrayList g2 = new ArrayList();
				HashMap<Integer, Integer> N1 = getN1(Label);
								
				result = VF2(M, g2, dg, candidates, computeOrder(candidates, Q, dg, Label), queryNodes, Label, N1, result);
 
				T = "T:"+targetFiles.getName().trim();
				P = "P:"+file.getName().trim();
				
				BufferedReader br2 = new BufferedReader(new FileReader("src\\Proteins\\Proteins\\ground_truth\\Proteins.8.gtr"));

				String line2 = null;
				int TP = 0, FP = 0, FN = 0;
				while ((line2 = br2.readLine()) != null) {
					if(T.equals(line2)) {
						if(P.equals(br2.readLine())) {
							int n = Integer.valueOf(br2.readLine().trim().split(":")[1]);
								for(int z = 0; z < n; z++) {
									line2 = br2.readLine().trim();
									if(result.containsKey(line2)) {
										TP++; 
									}
									else 
										FN++;
								}	
									
							break;		
						}
					}
					
				}
				FP = Math.abs(result.size() - TP);
				System.out.println("TP:" + TP + "  FP:" + FP + " FN:" + FN);
				System.out.println();
				
				long t2 = System.currentTimeMillis();
				System.out.println("Time taken: "+(t2-t1) +" millis");				
			}
		}
	}
	
	
	
//	Compute Order
	
	public static ArrayList<String> getNeighbourNodes(ArrayList<String> selectedNodes, DefaultUndirectedGraph<String, DefaultEdge> dg){
		
		ArrayList<String> al = new ArrayList<String>(); 
		
		for(String node: selectedNodes) {
			for(String n: neighboursOfQueryGraph(dg, node)) {
				if(!selectedNodes.contains(n)) {
					if(!al.contains(n)) {
						al.add(n);
					}
				}
			}
		}
		
		return al;
	}
	
	public static double getRValue(String Label) throws IOException {
		
		BufferedReader br = new BufferedReader(new FileReader("src\\Proteins\\Proteins\\target\\"+Label +".grf"));
		int numOfNodes = Integer.parseInt(br.readLine().trim());
		int i = 0;
		int q = 0;
		String line = null;
		String row[] = null;
		int numberOfEdges = 0;
		
		while ((line = br.readLine()) != null) {
			row = line.trim().split(" ");
			int nodeID = 0; 
			
			if(row.length == 2) { 
				if(i > numOfNodes) {
					numberOfEdges++;
				}
			}
			i++;
		}
		return (double)(numberOfEdges-1) / ( numOfNodes*(numOfNodes-1)/2 );
	}
	
	public static int getRPower(DefaultUndirectedGraph<String, DefaultEdge> dg, String node, ArrayList<String> selectedNodes) {
		int count = 0;
		for(String n: neighboursOfQueryGraph(dg, node)) {
			if(selectedNodes.contains(n))
				count++;
		}
		return count;
	}
	
	public static ArrayList<String> computeOrder(ArrayList<Integer>[] candidates, ArrayList<String> Q, DefaultUndirectedGraph<String, DefaultEdge> dg, String Label) throws IOException {
		
		
		double r = getRValue(Label);
		
		HashMap<String, String> qnodes = new HashMap<String, String>();
		for(String node: Q) {
			qnodes.put(node.substring(1, node.length()), node.charAt(0)+"");
		}
		int min = 9999, index = 0;
		for(int i = 0; i < candidates.length; i++) {
			if(candidates[i].size() < min) {
				min = candidates[i].size();
				index = i;
			}
		}
		
		
		ArrayList<String> selectedNodes = new ArrayList<String>();
		selectedNodes.add(qnodes.get(index+"") + index);
		double size = candidates[index].size();
		String sNode = null;
		double size1 = 9999999;
		while(true) {
			sNode = null;
			size1 = 9999999;
			
			for(String node: getNeighbourNodes(selectedNodes, dg)) {
				
				double rpow = getRPower(dg, node, selectedNodes); 
				if(size * candidates[Integer.parseInt(node.substring(1,node.length()))].size() * Math.pow(r, rpow) < size1) {
					size1 = size * candidates[Integer.parseInt(node.substring(1,node.length()))].size() * Math.pow(r, rpow);
					sNode = node;
				}
			}
			size = size1;
			selectedNodes.add(sNode);
			if(selectedNodes.size() == Q.size())
				break;
			
		}
		
		return selectedNodes;
		
	}
	
//	VF2
	public static boolean rule1(HashMap<String, Integer> M, String queryNode, int vid, String Label, DefaultUndirectedGraph<String, DefaultEdge> dg) {
		
		boolean flag1= false;
		ArrayList<Integer> al1 = new ArrayList<Integer>();
		for(int i: neighboursOfDataGraph(Label, vid)) {
			if(M.containsValue(i)) 
				al1.add(i);
		}
		
		List<String> al2 = neighboursOfQueryGraph(dg, queryNode);
		for(int i: al1) {
			flag1 = false;
			for(String j: al2) {
				if(M.containsKey(j)) {
					int v = M.get(j);
					if(v == i) {
						flag1 = true;
					}
				}
			}
			if(flag1 == false)
				return false;
		}	
		
		boolean flag2= false;
		ArrayList<String> al3 = new ArrayList<String>();
		for(String i: neighboursOfQueryGraph(dg, queryNode)) {
			if(M.containsKey(i)) 
				al3.add(i);
		}
		
		List<Integer> al4 = neighboursOfDataGraph(Label, vid);
		for(String i: al3) {
			flag2 = false;
			for(int j: al4) {
				if(M.containsKey(i)) {
					int v = M.get(i);
					if(v == j) {
						flag2 = true;
					}
				}
			}
			if(flag2 == false) {
				return false;
			}
		}	
				
		return true;
	}
	
	public static boolean rule2(HashMap<Integer, Integer> T1, HashMap<String, Integer> T2,  String queryNode,
			int vid, String Label, DefaultUndirectedGraph<String, DefaultEdge> dg) {
		
		int count1 = 0, count2 = 0;
		
		for(int i: neighboursOfDataGraph(Label, vid)) {
			if(T1.containsKey(i))
				count1++;
		}
		
		for(String i: neighboursOfQueryGraph(dg, queryNode)) {
			if(T2.containsKey(i))
				count2++;
		}
		
		return count1 >= count2;
	}
	
	public static boolean rule3(HashMap<Integer, Integer> N1T, HashMap<String, Integer> N2T,  String queryNode,
			int vid, String Label, DefaultUndirectedGraph<String, DefaultEdge> dg) {
		
		int count1 = 0, count2 = 0;
		
		for(int i: neighboursOfDataGraph(Label, vid)) {
			if(N1T.containsKey(i))
				count1++;
		}
		
		for(String i: neighboursOfQueryGraph(dg, queryNode)) {
			if(N2T.containsKey(i))
				count2++;
		}
		return count1 >= count2;
	}
	
	public static ArrayList<Integer> getTnCandidates(ArrayList<Integer> c, HashMap<Integer,Integer> T1){
		ArrayList<Integer> cand = new ArrayList<Integer>();
		
		for(Integer i : c) {
			if(T1.containsKey(i)) {
				cand.add(i);
			}
		}
		
		if(cand.size() == 0)
			return c;
		else
			return cand;
	}
	
	
	public static HashMap<String, Integer> VF2(HashMap<String, Integer> M, ArrayList g2, DefaultUndirectedGraph<String, DefaultEdge> dg,
			ArrayList<Integer> candidates[], ArrayList<String> order, HashMap<String, Integer> queryNodes,
			String Label, HashMap<Integer, Integer> N1, HashMap<String, Integer> result) {
	
		if (M.size() == order.size()) {
			HashMap<String, Integer> M2 = new HashMap<String, Integer>();
			for (Map.Entry<String, Integer> nodes : M.entrySet()) {
				M2.put(nodes.getKey().substring(1, nodes.getKey().length()), nodes.getValue());
			}
			String result1 = "S:"+ M.size()+":"; 
			for (int i = 0; i < M2.size(); i++) {
				result1 += i+","+M2.get(i+"")+";";
			}
			result1 = result1.substring(0, result1.length()-1).trim();
			System.out.println(result1);
			result.put(result1, 1);
			
			return result;
		}
		else {
			
			String u = null;
			for(String node: order) {
				if(!queryNodes.containsKey(node)) {
					u = node;
					break;
				}
			}

			HashMap<Integer, Integer> T1 = new HashMap<Integer, Integer>();
			HashMap<String, Integer> T2 = new HashMap<String, Integer>();
			int dataNode = 0;
			String queryNode = null;
			for (Map.Entry<String, Integer> nodes : M.entrySet()) {
				dataNode = nodes.getValue();
				queryNode = nodes.getKey();
				for(int a : neighboursOfDataGraph(Label, dataNode)) {
					T1.put(a, 1);
				}
				for(String a : neighboursOfQueryGraph(dg, queryNode)) {
					T2.put(a, 1);
				}
			}
			for (Map.Entry<String, Integer> nodes : M.entrySet()) {
				dataNode = nodes.getValue();
				queryNode = nodes.getKey();
				if(T1.containsKey(dataNode)) {
					T1.remove(dataNode);
				}
				if(T2.containsKey(queryNode)) {
					T2.remove(queryNode);
				}
			}
			
//			calculate N1T, N2T
			HashMap<Integer, Integer> N1T = (HashMap<Integer, Integer>)N1.clone();
			for (Map.Entry<String, Integer> nodes : M.entrySet()) {
				dataNode = nodes.getValue();
				N1T.remove(dataNode);
			}
			
			
			HashMap<String, Integer> N2T = new HashMap<String, Integer>();
			for(String x: order) {
				N2T.put(x, 1);
			}
			for (Map.Entry<String, Integer> nodes : M.entrySet()) {
				dataNode = nodes.getValue();
				queryNode = nodes.getKey();
				N2T.remove(queryNode);
			}
			
			ArrayList<Integer> cand = getTnCandidates(candidates[Integer.parseInt(u.substring(1, u.length()))], T1);
			
			for(Integer v: cand) {
				
				if(rule1(M, u, v, Label, dg) && rule2(T1, T2, u, v, Label, dg) && rule3(N1T, N2T, u, v, Label, dg)) {
					M.put(u, v);
					queryNodes.put(u, 1);
					
					result = VF2(M, g2, dg, candidates, order, queryNodes, Label, N1, result);
					M.remove(u);
					queryNodes.remove(u);
				}
			}
			
			
		}
		return result; 
		 
	}
	
	public static HashMap<Integer,Integer> getN1(String Label){
		String cypherQuery = "Match (n1:" + Label + ")"+" return distinct n1.OGID";
		
		HashMap<Integer,Integer> nodes = new HashMap<Integer,Integer>();
		Result res = db.execute(cypherQuery);
		Map<String, Object> data = null;
		try (Transaction tx=db.beginTx()) {
			while(res.hasNext()) {
				data = new HashMap<>();
				data = res.next(); 
				
				int n = Integer.parseInt(data.get("n1.OGID").toString()); 
				nodes.put(n, 1);
				data = null;
			}
			tx.success();
		}
		return nodes;
	}
	
	
	
	
	
//	Naive Subgraph
	
	public static List<String> neighboursnEmbedding(DefaultUndirectedGraph<String, DefaultEdge> dg, String node, HashMap<String, Integer> phai) {		
		List<String> neighbour = new ArrayList<String>(); 
		
		for( String n: Graphs.neighborListOf(dg, node)) {
			if(phai.containsKey(n)) {
				neighbour.add(n);
			}
		}
		return neighbour;
		
	}
	
	public static boolean edgeCheckingNonInduced(String u, Integer v, 
			HashMap<String, Integer> phai, DefaultUndirectedGraph<String, DefaultEdge> dg, String Label) {
	
		for(String neighbour: neighboursnEmbedding(dg, u, phai)) {
			
			String cypherQuery = "Match (n1:" + Label + ") -- (n2:" +Label+" ) where n1.OGID = \""+v + "\" and n2.OGID = \""
					+phai.get(neighbour)+"\" return distinct n1.OGID";
			 
			Result res = db.execute(cypherQuery); 
			if(!res.hasNext()) {
				return false;
			}
			res.next().get("n1.OGID");
		} 
		return true;
	}
	
	public static HashMap<String, Integer> search(ArrayList<String> Q, ArrayList<String> D, ArrayList<Integer> C[],
			HashMap<String, Integer> phai, DefaultUndirectedGraph<String, DefaultEdge> dg, String Label, 
			HashMap<String, Integer> queryNodes, HashMap<String, Integer> result) {
	
		if(phai.size() == Q.size()) {
			
			HashMap<String, Integer> M2 = new HashMap<String, Integer>();
			for (Map.Entry<String, Integer> nodes : phai.entrySet()) {
				M2.put(nodes.getKey().substring(1, nodes.getKey().length()), nodes.getValue());
			}
			String result1 = "S:"+ phai.size()+":"; 
			for (int i = 0; i < M2.size(); i++) {
				result1 += i+","+M2.get(i+"")+";";
			}
			result1 = result1.substring(0, result1.length()-1).trim();
			System.out.println(result1);
			result.put(result1, 1);
			
			return result;
		}
		else {
			String u = null;
//			Selects the query node
			for(String node: Q) {
				if(!queryNodes.containsKey(node)) {
					u = node;
					break;
				}
			}
				
			for(Integer v: C[Integer.parseInt(u.substring(1, u.length()))]) {
				
				if(!phai.containsValue(v)) {
				if(edgeCheckingNonInduced(u,v,phai,dg, Label)) {
					phai.put(u,v);
					queryNodes.put(u, 1);
					result = search(Q, D, C, phai, dg, Label,queryNodes, result);
					phai.remove(u);
					queryNodes.remove(u);
				}
				}
				
			}
			
		}
		return result;
	}
	

	public static ArrayList<Integer> forAllNeighbours(ArrayList<Integer> clique, String Label, int s) {
		
		ArrayList<Integer> neighbours = new ArrayList();
		
		ArrayList<Integer> n[] = new ArrayList[clique.size()];
		for(int i=0; i < n.length; i++) {
			n[i] = new ArrayList<Integer>();
		}
		
		int i = 0;
		for(int id: clique) {
			
			String cypherQuery = "Match (n1:" + Label + ") -[*1.."+ s +"]- (n2:" + Label + ") where n1.OGID = \""+ id 
					+"\" return distinct n2.OGID, n2.attribute";
			
			Result res = db.execute(cypherQuery);
			Map<String, Object> data = null;
			try (Transaction tx=db.beginTx()) {
				while(res.hasNext()) {
					
					data = new HashMap<>();
					data = res.next(); 
					
					int neighbourId = Integer.parseInt(data.get("n2.OGID").toString()); 
					if(!n[i].contains(neighbourId)) {
						n[i].add(neighbourId);
					}
					if(!neighbours.contains(neighbourId)) {
						neighbours.add(neighbourId);
					}
					data = null;
				}
				tx.success();
			}
			i++;
		}
		
		for(int j=0; j < n.length; j++) {
			neighbours.retainAll(n[j]);
		}
		
		return neighbours;
	}
	
	public static ArrayList<Integer> forSomeNeighbours(ArrayList<Integer> clique, String Label) {
		
		ArrayList<Integer> neighbours = new ArrayList(); 
		for(int id: clique) {
			
			String cypherQuery = "Match (n1:" + Label + ") -- (n2:" + Label + ") where n1.OGID = \""+ id 
					+"\" return distinct n2.OGID, n2.attribute";
			
			Result res = db.execute(cypherQuery);
			Map<String, Object> data = null;
			try (Transaction tx=db.beginTx()) {
				while(res.hasNext()) {
					
					data = new HashMap<>();
					data = res.next(); 
					
					int neighbourId = Integer.parseInt(data.get("n2.OGID").toString()); 
					if(!neighbours.contains(neighbourId)) {
						neighbours.add(neighbourId);
					}
					data = null;
				}
				tx.success();
			}
		}
		
		return neighbours;
	}
	
	public static ArrayList<Integer> allNeighbours(ArrayList<Integer> clique, String Label, int s) {
		
		ArrayList<Integer> forAllNeighbour = forAllNeighbours(clique, Label, s);
		ArrayList<Integer> forSomeNeighbour = forSomeNeighbours(clique, Label);
		
		forAllNeighbour.retainAll(forSomeNeighbour);
		
		return forAllNeighbour;
	}
	
	
	public static ArrayList<Integer> extendedMax(ArrayList<Integer> clique, String Label, int s, ArrayList<Integer> graph1) {
		
		int arbitraryId;
		if(clique.size() == 0) {
			arbitraryId = 0;			//make dynamic
			clique.add(arbitraryId);
		}
		
		ArrayList<Integer> neighbours = null;
		while(true) {
			
			neighbours = allNeighbours(clique, Label, s);
			if(neighbours.size() == 0)
				break;
			
			
			for(int node: neighbours) {
				if(graph1.size() == 0) {
					if(!clique.contains(node))
						clique.add(node);
				}
				else {
					if(!clique.contains(node) && graph1.contains(node))
						clique.add(node);
				}
			}
			
		}
		return clique;
	}
}
