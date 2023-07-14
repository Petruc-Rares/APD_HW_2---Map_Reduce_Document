public class OutputFormat {
	String fileName;
	String rank;
	int maxWordSize;
	int noWordsMax;
	int idxFile;

	public OutputFormat(String fileName, String rank, int maxWordSize, int noWordsMax, int idxFile) {
		this.fileName = fileName;
		this.rank = rank;
		this.maxWordSize = maxWordSize;
		this.noWordsMax = noWordsMax;
		this.idxFile = idxFile;
	}

	@Override
	public String toString() {
		return fileName + ',' +
				rank + ',' +
				maxWordSize + ',' +
				noWordsMax;
	}
}
