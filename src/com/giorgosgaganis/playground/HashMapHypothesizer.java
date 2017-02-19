package com.giorgosgaganis.playground;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 ##Basic Hypothesis

 While one thread is putting entries to the hashmap and at
 the same time another thread is putting an entry it can be
 shown that one of the following can happen:

 - the number of keys that can be iterated is different
 from HashMap.size()
 - or a put from either thread can be lost.

 The first part of the hypothesis should be enough to
 conclusively say that the error is the internal thread
 safety of the hashmap.  It is wrong for a data-structure to
 report a size that is different from the items that can be
 iterated. This cannot happen due to my own surrounding code.

 ##Secondary Hypothesis

 Despite the above I have run the same code on a
 ConcurrentHashMap and hypothesized the errors should not
 happen when running against it.  This was to support the
 basic hypothesis but not to provide proof.

 Historically I have found this helpful to pinpoint the true
 source of the erroneous behavior and not be confused but
 surrounding issues. So I have decided to also include it
 in the code to demonstrate my approach to this particular
 problem.

 ##Results of running the code

 Both the basic and secondary hypothesis hold true on my
 system which has the following configuration:

 - JVM jdk1.8.0_111
 - OS Linux 4.8.0-28-generic #30-Ubuntu
 - CPU Intel(R) Core(TM) i7-4790K CPU @ 4.00GHz
 */
public class HashMapHypothesizer {
    private static final long MAX_TRIES = 1_000_000;

    private final Map<String, String> map;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public HashMapHypothesizer(Map<String, String> map) {
        this.map = map;
    }

    private boolean runExperiment() throws Exception{

        //Clear the map at the start of an experiment
        map.clear();

        // The entries that will be put in the map to disrupt the operation in the
        // other threads
        Map<String, String> disruptorMap = new HashMap<>();
        disruptorMap.put("0","0");
        disruptorMap.put("1","1");

        //Execute in first thread
        Future<?> futureA = executorService.submit(
                //Using a put all to make it more likely we are going to interleave
                () -> map.putAll(disruptorMap)
        );
        //Execute in second thread
        Future<?> futureB = executorService.submit(
                () -> map.put("a", "a")
        );

        futureA.get();
        futureB.get();

        int keyCount = 0;
        for (String key: map.keySet()) {
            keyCount++;
        }

        boolean sizeIsCorrupt =
                keyCount != map.size();

        if(sizeIsCorrupt) {
            System.out.println("Experiment failure because size has become corrupt "
                    + "keyCount = " + keyCount + ", map.size() = " + map.size());
        }

        Map<String, String> allExpectedEntries = new HashMap(disruptorMap);
        allExpectedEntries.put("a","a");

        boolean valuesAreMissingOrIncorrect = false;

        for(String dKey: allExpectedEntries.keySet()) {
            if(!map.containsKey(dKey)) {
                valuesAreMissingOrIncorrect = true;

                System.out.println("Experiment failure "
                        + "because put has been lost for item with key = "
                        + dKey);
            }
        }

        return !sizeIsCorrupt && !valuesAreMissingOrIncorrect;
    }

    private static void runExperimentsOnMap(Map<String, String> map) throws Exception {
        HashMapHypothesizer hypothesizer = new HashMapHypothesizer(map);

        for(int i =0 ; i < MAX_TRIES; i++) {

            boolean experimentSuccessful = hypothesizer.runExperiment();

            if (!experimentSuccessful){
                System.out.println("Failed after "
                        +  i + " tries\n");
                hypothesizer.executorService.shutdown();
                return;
            }
        }
        hypothesizer.executorService.shutdown();
        System.out.println("Run was completed without error for "
                + MAX_TRIES + " # of experiment tries \n");
    }


    public static void main(String[] args) throws Exception {
        System.out.println("---Running hypothesizer on a java.util.HashMap");
        runExperimentsOnMap(new HashMap<>());

        System.out.println("---Running hypothesizer on a java.util.concurrent.ConcurrentHashMap");
        runExperimentsOnMap(new ConcurrentHashMap<>());
    }
}
