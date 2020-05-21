import com.google.common.collect.Sets;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import java.io.*;
import java.util.*;
import java.util.logging.Handler;

public class GroundTruth {
    private static String path_target_file = "target file path";
    private static String path = "data file path";
    private static GraphDatabaseService graphDb = null;
    private static HashMap<Integer, HashSet<Integer>> sNeighborMap = new HashMap<>();
    private static Integer s_value = 2;
    private static HashSet<Set<Integer>> all_cliques = null;
    private static List<ArrayList<Integer>> all_cliques_copy = null;
    private static PrintWriter writer = null;
    private static StringBuilder sbd = null;


    protected static   List<ArrayList<Integer>> calculate_groundTruth(Integer s, String label, int count) {
    	all_cliques=new HashSet<>();
    	all_cliques_copy=new ArrayList<>();
        for(int i = 0 ; i< count ; i++)
        {
            Set<Integer> candidates = sNeighborMap.containsKey(i)? sNeighborMap.get(i):getSDistanceNodes(i, s,label);
            Set<Integer> clique = new HashSet<>();
            clique.add(i);
            candidates.remove(i);
            Set<Integer> current_candidates = new HashSet<>(candidates);
            ArrayList<Integer> ar = new ArrayList<>(candidates);
            
            for(Integer node: ar)
            {
                clique.add(node);
				calculate_groundTruth_helper(clique, node, s, current_candidates, label);
                clique.remove(node);
            }
        }
		return all_cliques_copy;
        
        
    }

    private static void calculate_groundTruth_helper(Set<Integer> clique, Integer node, Integer s,
                                                     Set<Integer> current_candidates, String label) {

        if(current_candidates.size() == 0 && !all_cliques.contains(clique)) {
            ArrayList<Integer> ar = new ArrayList<>(clique);
            String[] arr = new String[ar.size()];
            for(int i =0 ;i< ar.size();i++)
            {
                arr[i] = String.valueOf(ar.get(i));
            }
            String clique_str =    String.join(",",arr);
            ArrayList<Integer> temp=new ArrayList<>(clique);
            Collections.sort(temp);
            all_cliques_copy.add(temp);
            all_cliques.add(new HashSet<>(clique));
        } 

        Set<Integer> candidates = sNeighborMap.containsKey(node)? sNeighborMap.get(node):getSDistanceNodes(node, s,label);
        Set<Integer> intersection_candidate = new HashSet<>(Sets.intersection(current_candidates, candidates));
        ArrayList<Integer> intersection_candidate_arrayList = new ArrayList<>(intersection_candidate);

        for(Integer intersect_node: intersection_candidate_arrayList)
        {
            clique.add(intersect_node);
            intersection_candidate.remove(intersect_node);
            calculate_groundTruth_helper(clique, intersect_node, s, intersection_candidate, label);
            clique.remove(intersect_node);
            intersection_candidate.add(intersect_node);
        }
    }

    private static HashSet<Integer> getSDistanceNodes(int node, int s, String label){
        HashSet<Integer> resultSet = new HashSet<>();
        String query = "MATCH (u:" + label + "{iden:'" + node + "'}) -[*1.. " + s + "]-> (c) RETURN DISTINCT (c.iden);";
        Result result = graphDb.execute(query);
        while (result.hasNext()){
            Map<String, Object> map = result.next();
            for(Map.Entry<String, Object> entry : map.entrySet()){
                int value = Integer.valueOf(String.valueOf(entry.getValue()));
                    resultSet.add(value);

            }
        }

        sNeighborMap.put(node, resultSet);
        return resultSet;
    }

    private static Result executeQuery(String query){
        Result result = null;
        graphDb.beginTx();
        result = graphDb.execute(query);
        return result;
    }

    private static int readFile(File file){
        int nodes = 0;
        try {
            BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
            String st;
            while ((st = bufferedReader.readLine()) != null){
                String[] rowArray = st.split(" ");
                if(rowArray.length == 1){
                    nodes = Integer.parseInt(rowArray[0]);
                    break;
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return nodes;
    }

    private static void readDir(){
        System.out.println("Loading data...");
        File dir = new File(path_target_file_ketan);
        for(File file : dir.listFiles()){
            all_cliques = new HashSet<>();
            all_cliques_copy=new ArrayList<>();
            sbd = new StringBuilder();
            String ogfileName = file.getName();
            String fileName = ogfileName.split("\\.")[0];
            int node_count = readFile(file);

            System.out.println("Target: " + ogfileName);
            all_cliques_copy=calculate_groundTruth(s_value, fileName, node_count);
            writer.println("Target: " + ogfileName);
            writer.println("Count: " + all_cliques.size());
            writer.println(sbd.toString());

        }
    }

    protected static void setConfig(){
        graphDb = new GraphDatabaseFactory()
                .newEmbeddedDatabaseBuilder(new File("Graph_New"))
                .setConfig(GraphDatabaseSettings.pagecache_memory, "512M" )
                .setConfig(GraphDatabaseSettings.string_block_size, "60" )
                .setConfig(GraphDatabaseSettings.array_block_size, "300" )
                .newGraphDatabase();
    }

    public static void main(String[] args)
    {
        Handler fileHandler = null;
        try {
            writer = new PrintWriter("./Grouhd_Truth_"+s_value+".txt", "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
        setConfig();
        readDir();
    }

}