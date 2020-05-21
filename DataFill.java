
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.unsafe.batchinsert.BatchInserter;
import org.neo4j.unsafe.batchinsert.BatchInserters;

public class DataFill {
	public static void main(String[] args) {
		try {
			
			BatchInserter inserter=BatchInserters.inserter(new File("data file name"));
			
			ArrayList<String> arrli = null;
			String line = null;
			long overall_index=0;
			File folder = new File("target file name");
			File[] listOfFiles = folder.listFiles();
			BufferedReader br = null;
			String name="";
			String[] str =null;
			HashMap<String, Object> map=null;
			String size="";
			int s=0;

			String filename_label="";
			for (int i = 0; i < listOfFiles.length; i++) 
			{
				name = "target file path"+listOfFiles[i].getName();	

				arrli=new ArrayList<String>();

				br = new BufferedReader(new FileReader(name));
				size=br.readLine();
				while ((line = br.readLine()) != null) 
				{
					str= line.split(" ");
				if(str.length==1) 
				{
					arrli.add(str[0])	;
					
				}	
				}
				br = new BufferedReader(new FileReader(name));
				size=br.readLine();
				filename_label=listOfFiles[i].getName().split("grf")[0].replace(".", "").trim();
				s=Integer.valueOf(size);
				int index=0;
				while ((line = br.readLine()) != null) 
				{
					str= line.split(" ");
						if(str.length==2 && index<=s) 
						{
						map= new HashMap<>();
						map.put("iden", str[0]);
						map.put("node", str[1]);
						map.put("filename", filename_label);
						map.put("neighbors",arrli.get(Integer.valueOf(str[0])));
						inserter.createNode(overall_index+Long.valueOf(str[0]),map, Label.label(filename_label));				
					}
					if(str.length==2 && index>s) 
					{
						map= new HashMap<>();
					
						System.out.println(str[0]+"  "+str[1]);
						
						inserter.createRelationship(overall_index+Long.valueOf(str[0]),overall_index+Long.valueOf(str[1]), 
								RelationshipType.withName(""),map );

					}
					index++;
				}
				overall_index=overall_index+s;
			}
			inserter.shutdown();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}