import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

public class MyRunnableMap implements Runnable {
	private final String fileName;
	private final Integer offset;
	private final Long size;
	private final ExecutorService tpe;
	private final AtomicInteger inQueue;
	Boolean takeIntoAccount = true;
	int idxFile;
	StringBuilder content;

	public MyRunnableMap(String fileName, Integer offset, Long size, ExecutorService tpe, AtomicInteger inQueue,
						 int idxFile) {
		this.fileName = fileName;
		this.offset = offset;
		this.size = size;
		this.tpe = tpe;
		this.inQueue = inQueue;
		this.idxFile = idxFile;
	}

	@Override
	public void run() {
		// start the  process of reading
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(fileName));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		// check if there is word continuing to the right of the current fragment
		Boolean toRight = false;
		// check if there is word continuing to the left of the current fragment
		Boolean toLeft = false;
		// check if the current fragment contains a delimiter
		boolean hasDelim = false;
		// intialize content of the current fragment
		content = new StringBuilder();

		// neutral element, used for the first fragments of each document
		char characterBefore = '\t';
		char currentCharacter = 0;
		int firstDelim = -1;
		int offsetFirstDelim = -1;

		// get the character before the current fragment
		if (offset > 0) {
			try {
				reader.skip(offset - 1);
			} catch (IOException e) {
				e.printStackTrace();
			}

			try {
				characterBefore = (char) reader.read();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		// get the first character of the current fragment
		try {
			currentCharacter = (char) reader.read();
			content.append(currentCharacter);
		} catch (IOException e) {
			e.printStackTrace();
		}

		// check if there is continuity of a word to the left
		if ((!(Tema2.separators.contains(Character.toString(characterBefore))) &&
				!(Tema2.separators.contains(Character.toString(currentCharacter))))) {
			toLeft = true;
		}

		// now check for separator for cases like long words
		// e.g: aaaaaabbabababab, frag_size = 3, only one fragment should
		// hold the whole word, other should be '' (empty)
		if (Tema2.separators.contains(Character.toString(currentCharacter))) {
			hasDelim = true;
			firstDelim = currentCharacter;
			offsetFirstDelim = offset;
		}

		// read the other characters in the current fragment
		for (int i = offset + 1; i < offset + size; i++) {
			try {
				currentCharacter = (char) reader.read();
				content.append(currentCharacter);
			} catch (IOException e) {
				e.printStackTrace();
			}

			// check again for delimiter
			if (Tema2.separators.contains(Character.toString(currentCharacter))) {
				hasDelim = true;
				firstDelim = (firstDelim == -1) ? currentCharacter : firstDelim;
				offsetFirstDelim = (offsetFirstDelim == -1) ? i : offsetFirstDelim;
			}
		}

		// at the moment currentCharacter is the last character in the current sequence
		// check if need to extend to right
		char nextCharacter = 0;

		if (offset + size < Tema2.filesSize.get(idxFile)) {
			try {
				nextCharacter = (char) reader.read();
			} catch (IOException e) {
				e.printStackTrace();
			}

			// check if there is continuity of a word to the left
			if ((!(Tema2.separators.contains(Character.toString(nextCharacter))) &&
					!(Tema2.separators.contains(Character.toString(currentCharacter))))) {
				//System.out.println(fileName + " " + nextCharacter + " " + currentCharacter);

				toRight = true;
			}
		}

		// in case of the current fragment not holding a delimiter
		// but having a word starting from one of the previous fragments
		// then for sure the previous fragments will have toRight = true
		// and will contain the current fragments as well
		if ((toLeft) && (hasDelim == false) ) {
			// size = 0
			// offset doesn't matter, keep it the same
			toLeft = false;
			content.setLength(0);
		}


		// move the current fragment to the first delimiter in the fragment,
		// so the text before the delimiter is in the previous fragment
		if (toLeft) {
			int newSize = (int) (offset + size - offsetFirstDelim);

			tpe.submit(new MyRunnableMap(fileName, offsetFirstDelim, (long) newSize,
					tpe, inQueue, idxFile));

			takeIntoAccount = false;
		}

		// the current fragment extends to the next delimiter in the file
		// until the word is completely in the current fragment
		else if (toRight) {

			int offsetNextDelim = (int) (offset + size + 1);

			while (Tema2.separators.contains(Character.toString(nextCharacter))) {
				try {
					nextCharacter = (char) reader.read();
					offsetNextDelim++;
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			int newSize = (int) (offsetNextDelim - offset);

			tpe.submit(new MyRunnableMap(fileName, offset, (long) newSize,
					tpe, inQueue, idxFile));

			takeIntoAccount = false;
		}

		if (takeIntoAccount) {
			// get the words from the current fragment
			String[] words = content.toString().split(Tema2.separators);

			// get the list of the maps of the curernt document
			List<ResultMapFormat> listMaps = Tema2.partialFilesMapping.get(idxFile);

			// the local map word_size -> no_words
			Map<Integer, Integer> wordCounts = new HashMap<>();

			// info about the strings of max size in this fragment
			int maxLength = -1;
			List<String> maxLengthStrings = new ArrayList<>();
			for (int i = 0; i < words.length; i++) {
				int lengthWord = words[i].length();

				if (lengthWord != 0) {
					Integer noWords = wordCounts.get(lengthWord);
					noWords = (noWords == null) ? 1 : noWords + 1;
					wordCounts.put(lengthWord, noWords);

					if (lengthWord == maxLength) {
						maxLengthStrings.add(words[i]);
					}

					if (lengthWord > maxLength) {
						maxLength = lengthWord;
						maxLengthStrings = new ArrayList<>();
						maxLengthStrings.add(words[i]);
					}
				}
			}

			ResultMapFormat newMap = new ResultMapFormat(fileName, wordCounts, maxLengthStrings);
			listMaps.add(newMap);

			if (inQueue.decrementAndGet() == 0) {
				tpe.shutdown();
			}

		}
	}

	@Override
	public String toString() {
		return "MyRunnableMap{" +
				"fileName='" + fileName + '\'' +
				", offset=" + offset +
				", size=" + size +
				", tpe=" + tpe +
				", inQueue=" + inQueue +
				", contet=" + content +
				'}';
	}
}
