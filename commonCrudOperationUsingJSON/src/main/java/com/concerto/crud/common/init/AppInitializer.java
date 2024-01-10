package com.concerto.crud.common.init;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.concerto.crud.common.util.JsonToJavaConverter;
import com.concerto.crud.common.util.Logging;

/**
 * Copyright (C) Concerto Software and Systems (P) LTD | All Rights Reserved
 * 
 * @File : com.concerto.crud.common.init.AppInitializer.java
 * @Author : Suyog Kedar
 * @AddedDate : October 03, 2023 12:30:40 PM
 * @Purpose : Initializes and processes data on application startup using the
 *          JsonToJavaConverter.
 * @Version : 1.0
 */

@Component
public class AppInitializer implements CommandLineRunner {

	/* Field */
	private final JsonToJavaConverter jsonToJavaConverter;
	public static Properties props = new Properties();
	

	/* Constructor */

	@Autowired
	public AppInitializer(JsonToJavaConverter jsonToJavaConverter) {
		this.jsonToJavaConverter = jsonToJavaConverter;
		
	}

	/* CommandLineRunner run Method */

	@Override
	public void run(String... args) throws Exception {
		try {
			props.load(new FileInputStream(new File("C:\\Users\\gayatri.hande\\Desktop\\config.properties")));
		} catch (Exception e) {
			Logging.error("Error while getting file", e);
		}
		jsonToJavaConverter.moduleMap();

	}

	public static Properties getProps() {
		return props;
	}

	public static void setProps(Properties props) {
		AppInitializer.props = props;
	}

}
