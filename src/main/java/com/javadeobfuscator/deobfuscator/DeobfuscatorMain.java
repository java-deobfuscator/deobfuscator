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

import com.javadeobfuscator.deobfuscator.exceptions.NoClassInPathException;
import com.javadeobfuscator.deobfuscator.exceptions.PreventableStackOverflowError;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import org.apache.commons.cli.*;

import java.io.File;

public class DeobfuscatorMain {
    public static void main(String[] args) {
        System.exit(run(args));
    }

    public static int run(String[] args) {
        Options options = new Options();
        options.addOption("transformer", true, "A transformer to use");
        options.addOption("path", true, "A JAR to be placed in the classpath");
        options.addOption("input", true, "The input file");
        options.addOption("output", true, "The output file");
        //TODO:
        // * keepClass
        // * custom normalizer name

        CommandLineParser parser = new DefaultParser();
        try {
            Deobfuscator deobfuscator = new Deobfuscator();
            CommandLine cmd = parser.parse(options, args);

            if (!cmd.hasOption("input")) {
                System.out.println("No input jar specified");
                return 3;
            }

            if (!cmd.hasOption("output")) {
                System.out.println("No output jar specified");
                return 4;
            }

            File input = new File(cmd.getOptionValue("input"));
            if (!input.exists()) {
                System.out.println("Input file does not exist");
                return 5;
            }

            File output = new File(cmd.getOptionValue("output"));
            if (output.exists()) {
                System.out.println("Warning! Output file already exists");
            }

            deobfuscator.withInput(input).withOutput(output);

            String[] transformers = cmd.getOptionValues("transformer");
            if (transformers == null || transformers.length == 0) {
                System.out.println("No transformers specified");
                return 2;
            }
            for (String transformer : transformers) {
                Class<?> clazz = null;
                try {
                    clazz = Class.forName("com.javadeobfuscator.deobfuscator.transformers." + transformer);
                } catch (ClassNotFoundException exception) {
                    try {
                        clazz = Class.forName(transformer);
                    } catch (ClassNotFoundException exception1) {
                        System.out.println("Could not locate transformer " + transformer);
                    }
                }
                if (clazz != null) {
                    if (Transformer.class.isAssignableFrom(clazz)) {
                        deobfuscator.withTransformer(clazz.asSubclass(Transformer.class));
                    } else {
                        System.out.println(clazz.getCanonicalName() + " does not extend com.javadeobfuscator.deobfuscator.transformers.Transformer");
                    }
                }
            }

            String[] paths = cmd.getOptionValues("path");
            if (paths != null) {
                for (String path : paths) {
                    File file = new File(path);
                    if (file.exists()) {
                        deobfuscator.withClasspath(file);
                    } else {
                        System.out.println("Could not find classpath file " + path);
                    }
                }
            }

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
        } catch (ParseException e) {
            return 1;
        }
    }
}
