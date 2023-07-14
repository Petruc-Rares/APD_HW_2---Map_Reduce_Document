import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Tema2 {
    /** number of workers (threads) that the program will have - args[0] **/
    static Integer noWorkers;
    /** the name of the input file, where the data is read from - args[1] **/
    static String inputFileName;
    /** the name of the output file, where the data is written to - args[2] **/
    static String outputFileName;
    /** the number of bytes a fragment has **/
    static Long sizeFragment;
    /** the total number of documents that are processed **/
    static Integer noFiles;
    /** the names of the files that are processed **/
    static List<String> filesNames;
    /** list that has size for each of the files that are processed **/
    static List<Long> filesSize;
    /** regex syntax used for specifying the delimiters used in this homework**/
    static String separators = "[\\;\\:\\/\\?\\~\\.,\\>\\<\\[\\]\\{\\}\\(\\)\\!\\@\\#\\$\\%\\^\\&\\-\\_\\+\\'\\=\\*\"\\| \t\r\n]";
    /** map from the index of each file to the partial data obtained from each fragment **/
    static ConcurrentMap<Integer, List<ResultMapFormat>> partialFilesMapping = new ConcurrentHashMap<>();
    /** map from the index of each file to the merged data of a file **/
    static ConcurrentMap<Integer, ResultMapFormat> fullFilesMapping = new ConcurrentHashMap<>();
    /** array holding the values of the ranks of each file **/
    static float ranks[];
    /** the data that will be written to the ouput file **/
    static List<OutputFormat> toWriteInfo = new ArrayList<>();

    /** this function computes and returns the n-th fibonacci number, starting from 0, 1
     * @param n - the position in the Fibonacci sequence
     * **/
    static int fib(int n) {
        int prev = 0;
        int curr = 1;

        if ((n == 0) || (n == 1)) {
            return 1;
        }

        while (n > 1) {
            int aux = curr;
            curr = prev + curr;
            prev = aux;
            n--;
        }

        return curr;
    }

    /** this method is responsible for reading the input file of Homework 2 at PDA
       and setting the adequate fields (sizeFragment, noFiles, filesNames) **/
    private static void readInputFile() {
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(inputFileName));

            // first line is the size of a fragment;
            String line = reader.readLine();
            sizeFragment = Long.parseLong(line);

            // second line is the number of documents
            line = reader.readLine();
            noFiles = Integer.parseInt(line);

            // the other lines represent the names
            // of the documents
            filesNames = new ArrayList<>();
            line = reader.readLine();
            while (line != null) {
                filesNames.add(line);
                line = reader.readLine();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** this methods computes the size of the files mentioned in the input file
       and fills the filesSize array for each **/
    public static void computeSizeFiles() {
        filesSize = new ArrayList<>();

        for (int itFiles = 0; itFiles < filesNames.size(); itFiles++) {
            Path path = Paths.get(filesNames.get(itFiles));
            try {
                long sizeFile = Files.size(path);
                filesSize.add(sizeFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /** this method writes the data in the desired format to the outputFile **/
    static void writeOutputFile() {
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(outputFileName, true));
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (int i = 0; i < toWriteInfo.size(); i++) {
            try {
                writer.append(toWriteInfo.get(i).toString());
                writer.append('\n');
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** this method computes the ranks of each file, after the given formula ***/
    static void computeRanks() {
        ranks = new float[noFiles];

        ForkJoinPool forkJoinPool = new ForkJoinPool(noWorkers);
        for (int i = 0; i < noFiles; i++) {
            ResultMapFormat crtFileMapping = fullFilesMapping.get(i);

            int noTotalWords = 0;

            for (Map.Entry<Integer, Integer> entry : crtFileMapping.getWordCounts().entrySet()) {
                FibonacciCalculator calculator = new FibonacciCalculator(entry.getKey() + 1);
                forkJoinPool.execute(calculator);

                try {
                    ranks[i] += calculator.get() * entry.getValue();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
                noTotalWords += entry.getValue();
            }

            ranks[i] /= noTotalWords;
            // now, all the data needed to be written to output is available
            toWriteInfo.add(new OutputFormat(filesNames.get(i).replaceFirst("tests/files/", ""), String.format("%.2f", ranks[i]), crtFileMapping.maxLengthWords.get(0).length(),crtFileMapping.maxLengthWords.size(), i));
        }

        // sort the output data descending after rank
        toWriteInfo.sort(new Comparator<OutputFormat>() {
            @Override
            public int compare(
                    OutputFormat o1, OutputFormat o2) {
                float x = (float) (Float.parseFloat(o2.rank) - Float.parseFloat(o1.rank));
                if (x > 0) return 1;
                if (x < 0) return -1;
                if (x == 0) return o1.idxFile - o2.idxFile;
                return 0;
            }
        });
    }

    public static void main(String[] args) {

        if (args.length < 3) {
            System.err.println("Usage: Tema2 <workers> <in_file> <out_file>");
            return;
        }

        // set arguments from command line accordingly
        noWorkers = Integer.parseInt(args[0]);
        inputFileName = args[1];
        outputFileName = args[2];

        readInputFile();

        computeSizeFiles();

        // the list in the right part of the map needs to be
        // synchronous, because it will be used concurrently
        for (int i = 0; i < noFiles; i++) {
            List<ResultMapFormat> init = partialFilesMapping.get(i);
            init = Collections.synchronizedList(new ArrayList<>());
            partialFilesMapping.put(i, init);
        }

        // will be used for representing the number of tasks
        // that are at a time in the queue for being executed
        AtomicInteger inQueue = new AtomicInteger();

        // set the pool of tasks
        ExecutorService tpe = Executors.newFixedThreadPool(noWorkers);

        // compute how many splits are done in all the documents
        // enumerated in the input file
        long noSplits = 0;
        for (int it = 0; it < filesSize.size(); it++) {
            noSplits += filesSize.get(it) / (sizeFragment) ;
            if (!(filesSize.get(it) % sizeFragment == 0)) {
                noSplits += 1;
            }
        }

        inQueue.set((int) noSplits);

        // now let's add the tasks in the pool in their initial form (not adjusted)
        for (int itFiles = 0; itFiles < filesNames.size(); itFiles++) {
            long sizeFile = filesSize.get(itFiles);

            for (int itFrag = 0; itFrag < sizeFile; itFrag += sizeFragment) {
                // last tasks might have a different size
                if (itFrag + sizeFragment > sizeFile) {
                    tpe.submit(new MyRunnableMap(filesNames.get(itFiles), itFrag, sizeFile - itFrag,
                                                tpe, inQueue,  itFiles));
                } else {
                    tpe.submit(new MyRunnableMap(filesNames.get(itFiles), itFrag, sizeFragment,
                            tpe, inQueue, itFiles));
                }
            }
        }

        // wait for all previous mapping tasks to end
        try {
            tpe.awaitTermination(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // the reduce merge starts
        tpe = Executors.newFixedThreadPool(noWorkers);

        // now we have the number of tasks equal to the number of files
        // in the input file
        inQueue.set(noFiles);
        for (int i = 0; i < noFiles; i++) {
            tpe.submit(new MyRunnableMerge(i, inQueue, tpe));
        }

        // wait for the merge reduce tasks to end
        try {
            tpe.awaitTermination(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        computeRanks();

        writeOutputFile();
    }
}
