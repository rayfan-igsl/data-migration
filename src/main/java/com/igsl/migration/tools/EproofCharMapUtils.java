package com.igsl.migration.tools;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

@Component
public class EproofCharMapUtils {

	private static final Map<String, String> dataMap = new HashMap<>();

	 @Autowired
	 private ResourceLoader resourceLoader;
	 
    @PostConstruct
    public void init() {
        loadFileData("classpath:/eproof/charmap.txt");
    }

    private void loadFileData(String filePath) {
        Resource resource = resourceLoader.getResource(filePath);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    dataMap.put(parts[0].trim(), parts[1].trim());
                }
            }
        } catch (IOException e) {
            e.printStackTrace(); // Handle the exception as needed
        }
    }

    public static Map<String, String> getDataMap() {
        return dataMap;
    }
}