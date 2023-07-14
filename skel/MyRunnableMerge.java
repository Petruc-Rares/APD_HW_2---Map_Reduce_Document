import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

public class MyRunnableMerge implements Runnable {
	int idxFile;
	AtomicInteger inQueue;
	ExecutorService tpe;

	public MyRunnableMerge(int idxFile, AtomicInteger inQueue, ExecutorService tpe) {
		this.idxFile = idxFile;
		this.inQueue = inQueue;
		this.tpe = tpe;
	}

	@Override
	public void run() {
		List<ResultMapFormat> partialResults =  Tema2.partialFilesMapping.get(idxFile);

		// the list containing all the strings of max size in the documents
		List<String> maxLengthStrings = new ArrayList<>();
		// map from word_size -> word_count for each word in the document
		Map<Integer, Integer> wordCountsTotal = new HashMap<>();
		Integer crtMaxSize = -1;

		// fill the map and the list
		for (int i = 0; i < partialResults.size(); i++) {
			ResultMapFormat partialResult = partialResults.get(i);

			Map<Integer, Integer> localWordCounts = partialResult.getWordCounts();

			for (Map.Entry<Integer, Integer> entry : localWordCounts.entrySet()) {
				Integer key = entry.getKey();
				Integer value = entry.getValue();

				Integer knownValue = wordCountsTotal.get(key);
				knownValue = (knownValue == null) ? value : knownValue + value;
				wordCountsTotal.put(key, knownValue);
			}

			// used for the list of words of max length in the current document
			Integer crtSize = 0;

			if (partialResult.maxLengthWords.size() > 0) {
				crtSize = partialResult.maxLengthWords.get(0).length();
			}

			if (crtSize <= 0) continue;;

			if (crtSize == crtMaxSize) {
				maxLengthStrings.addAll(partialResult.maxLengthWords);
			} else if (crtSize > crtMaxSize) {
				maxLengthStrings = new ArrayList<>();
				maxLengthStrings.addAll(partialResult.maxLengthWords);
				crtMaxSize = crtSize;
			}
		}

		Tema2.fullFilesMapping.put(idxFile, new ResultMapFormat(Tema2.filesNames.get(idxFile), wordCountsTotal,
														maxLengthStrings));

		if (inQueue.decrementAndGet() == 0) {
			tpe.shutdown();
		}
	}
}
