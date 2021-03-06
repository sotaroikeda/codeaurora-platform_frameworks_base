/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Writes /frameworks/base/preloaded-classes. Also updates LoadedClass.preloaded
 * fields and writes over compiled log file.
 */
public class WritePreloadedClassFile {

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        
        // Process command-line arguments first
        List<String> wiredProcesses = new ArrayList<String>();
        String inputFileName = null;
        int argOffset = 0;
        try {
            while ("--preload-all-process".equals(args[argOffset])) {
                argOffset++;
                wiredProcesses.add(args[argOffset++]);
            }
            
            inputFileName = args[argOffset++];
        } catch (RuntimeException e) {
            System.err.println("Usage: WritePreloadedClassFile " +
                    "[--preload-all-process process-name] " +
                    "[compiled log file]");
            System.exit(0);
        }

        Root root = Root.fromFile(inputFileName);

        for (LoadedClass loadedClass : root.loadedClasses.values()) {
            loadedClass.preloaded = false;
        }

        Writer out = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(Policy.getPreloadedClassFileName()),
                Charset.forName("US-ASCII")));

        out.write("# Classes which are preloaded by com.android.internal.os.ZygoteInit.\n");
        out.write("# Automatically generated by /frameworks/base/tools/preload.\n");
        out.write("# percent=" + Proc.PERCENTAGE_TO_PRELOAD + ", weight="
                + ClassRank.SEQUENCE_WEIGHT
                + ", bucket_size=" + ClassRank.BUCKET_SIZE
                + "\n");
        for (String wiredProcess : wiredProcesses) {
            out.write("# forcing classes loaded by: " + wiredProcess + "\n");
        }

        Set<LoadedClass> highestRanked = new TreeSet<LoadedClass>();
        for (Proc proc : root.processes.values()) {
            // test to see if this is one of the wired-down ("take all classes") processes
            boolean isWired = wiredProcesses.contains(proc.name);
            
            List<LoadedClass> highestForProc = proc.highestRankedClasses(isWired);

            System.out.println(proc.name + ": " + highestForProc.size());

            for (LoadedClass loadedClass : highestForProc) {
                loadedClass.preloaded = true;
            }
            highestRanked.addAll(highestForProc);
        }

        for (LoadedClass loadedClass : highestRanked) {
            out.write(loadedClass.name);
            out.write('\n');
        }

        out.close();

        System.out.println(highestRanked.size()
                + " classes will be preloaded.");

        // Update data to reflect LoadedClass.preloaded changes.
        root.toFile(inputFileName);
    }
}
