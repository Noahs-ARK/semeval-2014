package edu.cmu.cs.ark.semeval2014.prune;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class PruneModel {
	public Map<String, Double> weights;

	public PruneModel(){
		weights = new HashMap<String, Double>();
	}

	public void save(String fileName) {
		try
		{
			FileOutputStream fileOut =
					new FileOutputStream(fileName);
			ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(weights);
			out.close();
			fileOut.close();
			System.out.println();
			System.out.printf("Serialized preprocessing model is saved in " + fileName + "\n");
		}catch(IOException i)
		{
			i.printStackTrace();
		}
	}

	public void load(String fileName){
		try
		{
			FileInputStream fileIn = new FileInputStream(fileName);
			ObjectInputStream in = new ObjectInputStream(fileIn);
			weights = (Map<String, Double>) in.readObject();
			in.close();
			fileIn.close();
		}catch(IOException i)
		{
			i.printStackTrace();
			return;
		}catch(ClassNotFoundException c)
		{
			System.out.println("Employee class not found");
			c.printStackTrace();
			return;
		}
		System.out.println("Successuflly loaded in weights file from " + fileName);
	}
}
