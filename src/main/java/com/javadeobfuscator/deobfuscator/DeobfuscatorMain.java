/*
 * Copyright 2016 Sam Sun <me@samczsun.com>
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.javadeobfuscator.deobfuscator;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.javadeobfuscator.deobfuscator.config.Configuration;
import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.config.TransformerConfigDeserializer;
import com.javadeobfuscator.deobfuscator.exceptions.NoClassInPathException;
import com.javadeobfuscator.deobfuscator.exceptions.PreventableStackOverflowError;
import com.javadeobfuscator.deobfuscator.transformers.DelegatingTransformer;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class DeobfuscatorMain {
    public static void main(String[] args) throws ClassNotFoundException {
        System.exit(run(args));
    }

    public static int run(String[] args) throws ClassNotFoundException {
        Logger logger = LoggerFactory.getLogger(DeobfuscatorMain.class);

        Options options = new Options();
        options.addOption("c", "config", true, "The configuration file to use");

        CommandLineParser cmdlineParser = new DefaultParser();
        CommandLine cmdLine;
        try {
            cmdLine = cmdlineParser.parse(options, args);
        } catch (ParseException e) {
            logger.error("An error occurred while parsing the commandline", e);
            return 1;
        }

        if (!cmdLine.hasOption("config")) {
            logger.error("A config file must be specified");
            return 2;
        }

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
                .registerModule(
                        new SimpleModule().addDeserializer(TransformerConfig.class, new TransformerConfigDeserializer(logger))
                );

        Configuration configuration;
        try {
            configuration = mapper.readValue(new File(cmdLine.getOptionValue("config")), Configuration.class);
        } catch (IOException e) {
            logger.error("An error occurred while parsing the configuration file", e);
            return 3;
        }

        if (configuration.getInput() == null) {
            logger.error("An input JAR must be specified");
            return 4;
        }

        if (configuration.isDetect()) {
            return run(configuration);
        }

        if (configuration.getOutput() == null) {
            logger.error("An output JAR must be specified");
            return 5;
        }

        if (configuration.getOutput().exists()) {
            logger.warn("The specified output JAR already exists!");
            File parent = configuration.getOutput().getParentFile();
            if (!configuration.getOutput().renameTo(new File(parent, configuration.getOutput().getName() + ".bak"))) {
                logger.warn("I was unable to back up the previous output JAR");
            }
        }

        if (configuration.getTransformers() == null || configuration.getTransformers().size() == 0) {
            logger.error("At least one transformer must be specified");
            return 6;
        }

        return run(configuration);
    }

    private static int run(Configuration configuration) {
        Deobfuscator deobfuscator = new Deobfuscator(configuration);

        try {
            deobfuscator.start();
            return 0;
        } catch (NoClassInPathException ex) {
            for (int i = 0; i < 5; i++)
                System.out.println();
            System.out.println("** DO NOT OPEN AN ISSUE ON GITHUB **");
            System.out.println("Could not locate a class file.");
            System.out.println("Have you added the necessary files to the -path argument?");
            System.out.println("The error was:");
            ex.printStackTrace(System.out);
            return -2;
        } catch (PreventableStackOverflowError ex) {
            for (int i = 0; i < 5; i++)
                System.out.println();
            System.out.println("** DO NOT OPEN AN ISSUE ON GITHUB **");
            System.out.println("A StackOverflowError occurred during deobfuscation, but it is preventable");
            System.out.println("Try increasing your stack size using the -Xss flag");
            System.out.println("The error was:");
            ex.printStackTrace(System.out);
            return -3;
        } catch (Throwable t) {
            for (int i = 0; i < 5; i++)
                System.out.println();
            System.out.println("Deobfuscation failed. Please open a ticket on GitHub and provide the following error:");
            t.printStackTrace(System.out);
            return -1;
        }
    }
}
