package com.the_qa_company.qendpoint.core.triples.impl;

import com.the_qa_company.qendpoint.core.dictionary.Dictionary;
import com.the_qa_company.qendpoint.core.enums.TripleComponentOrder;
import com.the_qa_company.qendpoint.core.exceptions.IllegalFormatException;
import com.the_qa_company.qendpoint.core.exceptions.NotImplementedException;
import com.the_qa_company.qendpoint.core.hdt.HDTVocabulary;
import com.the_qa_company.qendpoint.core.header.Header;
import com.the_qa_company.qendpoint.core.iterator.SuppliableIteratorTripleID;
import com.the_qa_company.qendpoint.core.listener.ProgressListener;
import com.the_qa_company.qendpoint.core.options.ControlInfo;
import com.the_qa_company.qendpoint.core.options.HDTOptions;
import com.the_qa_company.qendpoint.core.options.HDTOptionsKeys;
import com.the_qa_company.qendpoint.core.triples.IteratorTripleID;
import com.the_qa_company.qendpoint.core.triples.TempTriples;
import com.the_qa_company.qendpoint.core.triples.TripleID;
import com.the_qa_company.qendpoint.core.triples.TriplesPrivate;
import com.the_qa_company.qendpoint.core.util.BitUtil;
import com.the_qa_company.qendpoint.core.compact.bitmap.AppendableWriteBitmap;
import com.the_qa_company.qendpoint.core.compact.sequence.SequenceLog64BigDisk;
import com.the_qa_company.qendpoint.core.util.io.CloseSuppressPath;
import com.the_qa_company.qendpoint.core.util.io.CountInputStream;
import com.the_qa_company.qendpoint.core.util.io.IOUtil;
import com.the_qa_company.qendpoint.core.util.listener.IntermediateListener;
import com.the_qa_company.qendpoint.core.util.listener.ListenerUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Appendable write {@link BitmapTriples} version
 *
 * @author Antoine Willerval
 */
public class WriteBitmapTriples implements TriplesPrivate {
	protected TripleComponentOrder order;
	private long numTriples;
	private final AppendableWriteBitmap bitY, bitZ;
	private final CloseSuppressPath seqY, seqZ, triples;
	private SequenceLog64BigDisk vectorY, vectorZ;

	public WriteBitmapTriples(HDTOptions spec, CloseSuppressPath triples, int bufferSize) throws IOException {
		String orderStr = spec.get(HDTOptionsKeys.TRIPLE_ORDER_KEY);
		if (orderStr == null) {
			this.order = TripleComponentOrder.SPO;
		} else {
			this.order = TripleComponentOrder.valueOf(orderStr);
		}
		triples.mkdirs();
		triples.closeWithDeleteRecurse();
		this.triples = triples;
		bitY = new AppendableWriteBitmap(triples.resolve("bitmapY"), bufferSize);
		bitZ = new AppendableWriteBitmap(triples.resolve("bitmapZ"), bufferSize);
		seqY = triples.resolve("seqY");
		seqZ = triples.resolve("seqZ");
	}

	@Override
	public void save(OutputStream output, ControlInfo ci, ProgressListener listener) throws IOException {
		ci.clear();
		ci.setFormat(getType());
		ci.setInt("order", order.ordinal());
		ci.setType(ControlInfo.Type.TRIPLES);
		ci.save(output);

		IntermediateListener iListener = new IntermediateListener(listener);
		bitY.save(output, iListener);
		bitZ.save(output, iListener);
		vectorY.save(output, iListener);
		vectorZ.save(output, iListener);
	}

	@Override
	public IteratorTripleID searchAll() {
		throw new NotImplementedException();
	}

	@Override
	public SuppliableIteratorTripleID search(TripleID pattern) {
		throw new NotImplementedException();
	}

	@Override
	public long getNumberOfElements() {
		return numTriples;
	}

	@Override
	public long size() {
		return numTriples * 4;
	}

	@Override
	public void populateHeader(Header header, String rootNode) {
		if (rootNode == null || rootNode.length() == 0) {
			throw new IllegalArgumentException("Root node for the header cannot be null");
		}

		header.insert(rootNode, HDTVocabulary.TRIPLES_TYPE, getType());
		header.insert(rootNode, HDTVocabulary.TRIPLES_NUM_TRIPLES, getNumberOfElements());
		header.insert(rootNode, HDTVocabulary.TRIPLES_ORDER, order.toString());
//		header.insert(rootNode, HDTVocabulary.TRIPLES_SEQY_TYPE, seqY.getType() );
//		header.insert(rootNode, HDTVocabulary.TRIPLES_SEQZ_TYPE, seqZ.getType() );
//		header.insert(rootNode, HDTVocabulary.TRIPLES_SEQY_SIZE, seqY.size() );
//		header.insert(rootNode, HDTVocabulary.TRIPLES_SEQZ_SIZE, seqZ.size() );
//		if(bitmapY!=null) {
//			header.insert(rootNode, HDTVocabulary.TRIPLES_BITMAPY_SIZE, bitmapY.getSizeBytes() );
//		}
//		if(bitmapZ!=null) {
//			header.insert(rootNode, HDTVocabulary.TRIPLES_BITMAPZ_SIZE, bitmapZ.getSizeBytes() );
//		}
	}

	@Override
	public String getType() {
		return HDTVocabulary.TRIPLES_TYPE_BITMAP;
	}

	@Override
	public TripleID findTriple(long position) {
		throw new NotImplementedException();
	}

	@Override
	public void load(InputStream input, ControlInfo ci, ProgressListener listener) {
		throw new NotImplementedException();
	}

	@Override
	public void mapFromFile(CountInputStream in, File f, ProgressListener listener) {
		throw new NotImplementedException();
	}

	@Override
	public void generateIndex(ProgressListener listener, HDTOptions disk, Dictionary dictionary) {
		throw new NotImplementedException();
	}

	@Override
	public void loadIndex(InputStream input, ControlInfo ci, ProgressListener listener) {
		throw new NotImplementedException();
	}

	@Override
	public void mapIndex(CountInputStream input, File f, ControlInfo ci, ProgressListener listener) {
		throw new NotImplementedException();
	}

	@Override
	public void saveIndex(OutputStream output, ControlInfo ci, ProgressListener listener) {
		throw new NotImplementedException();
	}

	public BitmapTriplesAppender createAppender(long numElements, ProgressListener listener) {
		vectorY = new SequenceLog64BigDisk(seqY.toAbsolutePath().toString(), BitUtil.log2(numElements));
		vectorZ = new SequenceLog64BigDisk(seqZ.toAbsolutePath().toString(), BitUtil.log2(numElements));
		numTriples = 0;
		return new BitmapTriplesAppender(numElements, listener);
	}

	@Override
	public void load(TempTriples triples, ProgressListener listener) {
		triples.setOrder(order);
		triples.sort(listener);

		IteratorTripleID it = triples.searchAll();

		long number = it.estimatedNumResults();

		vectorY = new SequenceLog64BigDisk(seqY.toAbsolutePath().toString(), BitUtil.log2(number));
		vectorZ = new SequenceLog64BigDisk(seqZ.toAbsolutePath().toString(), BitUtil.log2(number));

		long lastX = 0, lastY = 0, lastZ = 0;
		long x, y, z;
		numTriples = 0;

		while (it.hasNext()) {
			TripleID triple = it.next();
			TripleOrderConvert.swapComponentOrder(triple, TripleComponentOrder.SPO, order);

			x = triple.getSubject();
			y = triple.getPredicate();
			z = triple.getObject();
			if (x == 0 || y == 0 || z == 0) {
				throw new IllegalFormatException("None of the components of a triple can be null");
			}

			if (numTriples == 0) {
				// First triple
				vectorY.append(y);
				vectorZ.append(z);
			} else if (x != lastX) {
				if (x != lastX + 1) {
					throw new IllegalFormatException(
							"Upper level must be increasing and correlative. " + x + " != " + lastX + "+ 1");
				}
				// X changed
				bitY.append(true);
				vectorY.append(y);

				bitZ.append(true);
				vectorZ.append(z);
			} else if (y != lastY) {
				if (y < lastY) {
					throw new IllegalFormatException(
							"Middle level must be increasing for each parent. " + y + " < " + lastY);
				}

				// Y changed
				bitY.append(false);
				vectorY.append(y);

				bitZ.append(true);
				vectorZ.append(z);
			} else {
				if (z < lastZ) {
					throw new IllegalFormatException(
							"Lower level must be increasing for each parent. " + z + " < " + lastZ);
				}

				// Z changed
				bitZ.append(false);
				vectorZ.append(z);
			}

			lastX = x;
			lastY = y;
			lastZ = z;

			ListenerUtil.notifyCond(listener, "Converting to BitmapTriples", numTriples, numTriples, number);
			numTriples++;
		}

		if (numTriples > 0) {
			bitY.append(true);
			bitZ.append(true);
		}

		vectorY.aggressiveTrimToSize();
		vectorZ.aggressiveTrimToSize();
	}

	@Override
	public TripleComponentOrder getOrder() {
		return order;
	}

	@Override
	public void close() throws IOException {
		IOUtil.closeAll(bitY, bitZ, vectorY, seqY, vectorZ, seqZ, triples);
	}

	public class BitmapTriplesAppender {
		long lastX = 0, lastY = 0, lastZ = 0;
		long x, y, z;
		final long number;
		final ProgressListener listener;

		private BitmapTriplesAppender(long number, ProgressListener listener) {
			this.number = number;
			this.listener = listener;
		}

		public void append(TripleID triple) {
			TripleOrderConvert.swapComponentOrder(triple, TripleComponentOrder.SPO, order);

			x = triple.getSubject();
			y = triple.getPredicate();
			z = triple.getObject();
			if (x == 0 || y == 0 || z == 0) {
				throw new IllegalFormatException("None of the components of a triple can be null");
			}

			if (numTriples == 0) {
				// First triple
				vectorY.append(y);
				vectorZ.append(z);
			} else if (x != lastX) {
				if (x != lastX + 1) {
					throw new IllegalFormatException(
							"Upper level must be increasing and correlative. " + x + " != " + lastX + "+ 1");
				}
				// X changed
				bitY.append(true);
				vectorY.append(y);

				bitZ.append(true);
				vectorZ.append(z);
			} else if (y != lastY) {
				if (y < lastY) {
					throw new IllegalFormatException(
							"Middle level must be increasing for each parent. " + y + " < " + lastY);
				}

				// Y changed
				bitY.append(false);
				vectorY.append(y);

				bitZ.append(true);
				vectorZ.append(z);
			} else {
				if (z < lastZ) {
					throw new IllegalFormatException(
							"Lower level must be increasing for each parent. " + z + " < " + lastZ);
				}

				// Z changed
				bitZ.append(false);
				vectorZ.append(z);
			}

			lastX = x;
			lastY = y;
			lastZ = z;

			ListenerUtil.notifyCond(listener, "Converting to BitmapTriples", numTriples, numTriples, number);
			numTriples++;
		}

		public void done() {
			if (numTriples > 0) {
				bitY.append(true);
				bitZ.append(true);
			}

			vectorY.aggressiveTrimToSize();
			vectorZ.aggressiveTrimToSize();
		}
	}
}
