package edu.umd.cloud9.collection.clue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.SequenceFile;

public class ScanBlockCompressedSequenceFile {

	public static void main(String[] args) throws IOException {
		if (args.length != 1) {
			System.out.println("usage: [SequenceFile]");
			System.exit(-1);
		}

		List<Long> seekPoints = new ArrayList<Long>();
		long pos = -1;
		long prevPos = -1;

		int prevDocno = 0;

		Path path = new Path(args[0]);
		Configuration config = new Configuration();
		
		// jld: FileSystem URI...
		FileSystem fs=path.getFileSystem(config);
		SequenceFile.Reader reader = new SequenceFile.Reader(fs, path, config);

		IntWritable key = new IntWritable();
		ClueWarcRecord value = new ClueWarcRecord();

		pos = reader.getPosition();
		int cnt = 0;
		while (reader.next(key, value)) {
			if (prevPos != -1 && prevPos != pos) {
				System.out.println("## beginning of block at " + prevPos + ", docno:" + prevDocno);
				seekPoints.add(prevPos);
			}

			System.out.println("offset:" + pos + "\tdocno:" + key + "\tdocid:" + value.getDocid());

			prevPos = pos;
			pos = reader.getPosition();
			prevDocno = key.get();

			cnt++;

			if (cnt > Integer.MAX_VALUE)
				break;
		}

		reader.close();

		reader = new SequenceFile.Reader(FileSystem.get(config), path, config);

		for (long p : seekPoints) {
			reader.seek(p);
			reader.next(key, value);
			System.out.println("seeking to pos " + p + "\tdocno:" + key + "\tdocid:"
					+ value.getDocid());
		}

		reader.close();
	}
}
