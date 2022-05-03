package com.the_qa_company.qendpoint.utils.io;

import org.apache.tomcat.util.http.fileupload.MultipartStream;
import org.rdfhdt.hdt.util.concurrent.ExceptionThread;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Arrays;

/**
 * Utility class to extract streams from a {@link javax.servlet.http.HttpServletRequest}.
 *
 * @author Antoine Willerval
 */
public class MultipartStreamExtractor implements AutoCloseable {
	private final MultipartStream multipartStream;
	/**
	 * next element
	 */
	private ExtractedStream next;
	/**
	 * wait thread for piped stream
	 */
	private ExceptionThread waitStream;
	/**
	 * is completed?
	 */
	private boolean end;
	/**
	 * is init?
	 */
	private boolean init;

	private String extractBoundary(HttpServletRequest request) {
		String boundaryHeader = "boundary=";
		int i = request.getContentType().indexOf(boundaryHeader) +
				boundaryHeader.length();
		return request.getContentType().substring(i);
	}

	/**
	 * create a extractor
	 *
	 * @param request request to read
	 */
	public MultipartStreamExtractor(HttpServletRequest request) throws IOException {
		String boundary = extractBoundary(request);
		this.multipartStream = new MultipartStream(request.getInputStream(),
				boundary.getBytes(), 1024, null);
	}

	/**
	 * has a next stream to read with {@link #next()}, will return the same value while {@link #next()} isn't called
	 *
	 * @return true if there is another stream, false otherwise
	 * @throws IOException          error while reading the next element
	 * @throws InterruptedException error while waiting for the next {@link com.the_qa_company.qendpoint.utils.io.MultipartStreamExtractor.ExtractedStream#readToStream()} completion
	 */
	public boolean hasNext() throws IOException, InterruptedException {
		if (end) {
			return false;
		}
		if (next != null) {
			return true;
		}
		if (waitStream != null) {
			try {
				waitStream.joinAndCrashIfRequired();
			} catch (ExceptionThread.ExceptionThreadException t) {
				Throwable cause = t.getCause();
				if (cause instanceof IOException) {
					throw (IOException) cause;
				}
				throw t;
			} finally {
				waitStream = null;
			}
		}
		if (init) {
			end = !multipartStream.readBoundary();
		} else {
			end = !multipartStream.skipPreamble();
			init = true;
		}
		if (end) {
			return false;
		}

		String header = multipartStream.readHeaders();
		next = new ExtractedStream(header);

		return true;
	}

	@Override
	public void close() throws IOException, InterruptedException {
		end = true;
		if (waitStream != null) {
			try {
				waitStream.joinAndCrashIfRequired();
			} catch (ExceptionThread.ExceptionThreadException t) {
				Throwable cause = t.getCause();
				if (cause instanceof IOException) {
					throw (IOException) cause;
				}
				throw t;
			} finally {
				waitStream = null;
			}
		}
	}

	/**
	 * @return next extracted element
	 * @throws IOException          same as {@link #hasNext()}
	 * @throws InterruptedException same as {@link #hasNext()}
	 */
	public ExtractedStream next() throws IOException, InterruptedException {
		if (!hasNext()) {
			return null;
		}
		ExtractedStream stream = this.next;
		this.next = null;
		return stream;
	}

	/**
	 * read each stream
	 *
	 * @param runnable runnable to handle a stream
	 * @throws IOException          hasNext io exception or runnable handle exception
	 * @throws InterruptedException hasNext interrupt exception or runnable handle exception
	 */
	public void forEach(StreamRunnable... runnable) throws IOException, InterruptedException {
		mainLoop:
		while (hasNext()) {
			ExtractedStream s = next();
			for (StreamRunnable run : runnable) {
				if (run.name.equals(s.getName())) {
					if (run.func.handle(s)) {
						continue mainLoop;
					} else {
						break mainLoop;
					}
				}
			}
			// pass stream
		}
	}

	public static class StreamRunnable {
		private final String name;
		private final StreamPredicate func;

		public StreamRunnable(String name, StreamPredicate func) {
			this.name = name;
			this.func = func;
		}
	}

	@FunctionalInterface
	public interface StreamPredicate {
		/**
		 * handle a stream
		 *
		 * @param stream the stream
		 * @return true if the multipart stream should continue, false otherwise
		 * @throws IOException          io
		 * @throws InterruptedException interruption
		 */
		boolean handle(ExtractedStream stream) throws IOException, InterruptedException;
	}

	public class ExtractedStream {
		private String name;
		private String filename;
		private final String[][] header;

		private ExtractedStream(String header) {
			this.header = readContentDispositionHeader(header);
			for (String[] h : this.header) {
				switch (h[0]) {
					case "name":
						this.name = cleanString(h[1]);
						break;
					case "filename":
						this.filename = cleanString(h[1]);
						break;

				}
			}
		}

		private String cleanString(String s) {
			if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
				return s.substring(1, s.length() - 1);
			}
			return s;
		}

		public String getFilename() {
			return filename;
		}

		public String getName() {
			return name;
		}

		public String getHeader(String key) {
			for (String[] h : header) {
				if (h[0].equals(key)) {
					return h[1];
				}
			}
			return null;
		}

		public String[][] getHeaders() {
			return header;
		}

		private String[][] readContentDispositionHeader(String header) {
			return Arrays.stream(header.split("[\n\r]"))
					.filter(s -> !s.isEmpty())
					.filter(s -> s.startsWith("Content-Disposition: "))
					.map(s -> s.substring("Content-Disposition: ".length()))
					.flatMap(s -> Arrays.stream(s.split("; ")))
					.map(s -> s.split("=", 2))
					.toArray(String[][]::new);
		}

		public void readTo(OutputStream stream) throws IOException {
			multipartStream.readBodyData(stream);
		}

		public InputStream readToStream() throws IOException {
			if (waitStream != null) {
				throw new IllegalArgumentException("A stream is already in use");
			}


			PipedInputStream pipedInputStream = new PipedInputStream();
			PipedOutputStream pipedOutputStream = new PipedOutputStream();
			pipedInputStream.connect(pipedOutputStream);

			waitStream = new ExceptionThread(() -> {
				try (pipedOutputStream) {
					readTo(pipedOutputStream);
				}
			}, "ReadToStreamPipeThread");
			waitStream.start();
			return pipedInputStream;
		}
	}
}
