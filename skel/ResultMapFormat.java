import java.util.List;
import java.util.Map;

public class ResultMapFormat {
	String fileName;
	Map<Integer, Integer> wordCounts;
	List<String> maxLengthWords;

	public ResultMapFormat(String fileName, Map<Integer, Integer> wordCounts, List<String> maxLengthWords) {
		this.fileName = fileName;
		this.wordCounts = wordCounts;
		this.maxLengthWords = maxLengthWords;
	}

	public Map<Integer, Integer> getWordCounts() {
		return wordCounts;
	}


	@Override
	public String toString() {
		return "ResultMap{" +
				"fileName='" + fileName + '\'' +
				", wordCounts=" + wordCounts +
				", maxLengthWords=" + maxLengthWords +
				'}';
	}
}
