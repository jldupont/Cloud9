/*
 * Cloud9: A MapReduce Library for Hadoop
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0 
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package edu.umd.cloud9.tuple;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.io.WritableComparator;

/**
 * <p>
 * Class that represents a tuple. Tuples are instantiated from a {@link Schema}.
 * The Tuple class implements WritableComparable, so it can be directly used as
 * MapReduce keys and values. The natural sort order of tuples is defined by an
 * internally-generated byte representation and is not based on field values.
 * </p>
 * 
 * <p>
 * All fields can either be indexed via its integer position or its field name.
 * Each field is typed, which can be determined via {@link #getFieldType(int)}.
 * Fields can either contain an object of the specified type or a special symbol
 * String. The method {@link #containsSymbol(int)} can be used to check if a
 * field contains a special symbol. If the field contains a special symbol,
 * {@link #get(int)} will return <code>null</code>. If the field does not
 * contain a special symbol, {@link #getSymbol(int)} will return a
 * <code>null</code>.
 * </p>
 * 
 * <p>
 * Here is a typical usage scenario for special symbols: say you had tuples that
 * represented <code>count(a, b)</code>, where <code>a</code> and
 * <code>b</code> are tokens you observe. There is often a need to compute
 * <code>count(a, *)</code>, for example, to derive conditional
 * probabilities. In this case, you can use a special symbol to represent the
 * <code>*</code>, and distinguish it from the lexical token '<code>*</code>'.
 * </p>
 * 
 */
public class Tuple implements WritableComparable {

	protected static final byte SYMBOL = 0;
	protected static final byte INT = 1;
	protected static final byte BOOLEAN = 2;
	protected static final byte LONG = 3;
	protected static final byte FLOAT = 4;
	protected static final byte DOUBLE = 5;
	protected static final byte STRING = 6;
	protected static final byte WRITABLE = 7;

	private Object[] mObjects;
	private String[] mSymbols;
	private String[] mFields;
	private Class<?>[] mTypes;

	private byte[] mBytes = null;

	private Map<String, Integer> mFieldLookup = null;

	protected Tuple(Object[] objects, String[] symbols, String[] fields,
			Class<?>[] types) {
		mObjects = objects;
		mSymbols = symbols;
		mFields = fields;
		mTypes = types;
	}

	/**
	 * Creates an empty Tuple. This constructor is needed by Hadoop's framework
	 * for deserializing Writable objects. The preferred way to instantiate
	 * tuples is through {@link Schema#instantiate(Object...)}.
	 */
	public Tuple() {
	}

	/**
	 * Factory method for deserializing a Tuple object.
	 * 
	 * @param in
	 *            raw byte source of the Tuple
	 * @return a new Tuple
	 * @throws IOException
	 */
	public static Tuple createFrom(DataInput in) throws IOException {
		Tuple tuple = new Tuple();
		tuple.readFields(in);

		return tuple;
	}

	/**
	 * Sets the object at a particular field (by position) in this Tuple.
	 * 
	 * @param i
	 *            field position
	 * @param o
	 *            object to set at the specified field
	 */
	public void set(int i, Object o) {
		mObjects[i] = o;

		// invalidate serialized representation
		mBytes = null;
	}

	/**
	 * Sets the object at a particular field (by name) in this Tuple.
	 * 
	 * @param field
	 *            field name
	 * @param o
	 *            object to set at the specified field
	 */
	public void set(String field, Object o) {
		if (mFieldLookup == null)
			initLookup();

		set(mFieldLookup.get(field), o);
	}

	/**
	 * Sets a special symbol at a particular field (by position) in this Tuple.
	 * 
	 * @param i
	 *            field position
	 * @param s
	 *            special symbol to set at specified field
	 */
	public void setSymbol(int i, String s) {
		mObjects[i] = null;
		mSymbols[i] = s;

		// invalidate serialized representation
		mBytes = null;
	}

	/**
	 * Sets a special symbol at a particular field (by name) in this Tuple.
	 * 
	 * @param field
	 *            field name
	 * @param s
	 *            special symbol to set at specified field
	 */
	public void setSymbol(String field, String s) {
		if (mFieldLookup == null)
			initLookup();

		setSymbol(mFieldLookup.get(field), s);
	}

	/**
	 * Returns object at a particular field (by position) in this Tuple. Returns
	 * <code>null</code> if the field contains a special symbol.
	 * 
	 * @param i
	 *            field position
	 * @return object at field, or <code>null</code> if the field contains a
	 *         special symbol
	 */
	public Object get(int i) {
		return mObjects[i];
	}

	/**
	 * Returns object at a particular field (by name) in this Tuple. Returns
	 * <code>null</code> if the field contains a special symbol.
	 * 
	 * @param field
	 *            field name
	 * @return object at field, or <code>null</code> if the field contains a
	 *         special symbol
	 */
	public Object get(String field) {
		if (mFieldLookup == null)
			initLookup();

		return get(mFieldLookup.get(field));
	}

	/**
	 * Returns special symbol at a particular field (by position). Returns
	 * <code>null</code> if the field does not contain a special symbol.
	 * 
	 * @param i
	 *            field position
	 * @return special symbol at field, or <code>null</code> if the field does
	 *         not contain a special symbol.
	 */
	public String getSymbol(int i) {
		if (mObjects[i] != null)
			return null;

		return mSymbols[i];
	}

	/**
	 * Returns special symbol at a particular field (by name). Returns
	 * <code>null</code> if the field does not contain a special symbol.
	 * 
	 * @param field
	 *            field name
	 * @return special symbol at field, or <code>null</code> if the field does
	 *         not contain a special symbol.
	 */
	public String getSymbol(String field) {
		if (mFieldLookup == null)
			initLookup();

		return getSymbol(mFieldLookup.get(field));
	}

	/**
	 * Determines if a particular field (by position) contains a special symbol.
	 * 
	 * @param i
	 *            field position
	 * @return <code>true</code> if the field contains a special symbol, or
	 *         <code>false</code> otherwise
	 */
	public boolean containsSymbol(int i) {
		return mObjects[i] == null;
	}

	/**
	 * Determines if a particular field (by name) contains a special symbol.
	 * 
	 * @param field
	 *            field name
	 * @return <code>true</code> if the field contains a special symbol, or
	 *         <code>false</code> otherwise
	 */
	public boolean containsSymbol(String field) {
		if (mFieldLookup == null)
			initLookup();

		return containsSymbol(mFieldLookup.get(field));
	}

	/**
	 * Returns the type of a particular field (by position).
	 * 
	 * @param i
	 *            field position
	 * @return type of the field
	 */
	public Class<?> getFieldType(int i) {
		return mTypes[i];
	}

	/**
	 * Returns the type of a particular field (by name).
	 * 
	 * @param field
	 *            field name
	 * @return type of the field
	 */
	public Class<?> getFieldType(String field) {
		if (mFieldLookup == null)
			initLookup();

		return getFieldType(mFieldLookup.get(field));
	}

	/**
	 * Lazily construct the lookup table for this schema. Used to accelerate
	 * name-based lookups of schema information.
	 */
	private void initLookup() {
		mFieldLookup = new HashMap<String, Integer>();
		for (int i = 0; i < mFields.length; ++i) {
			mFieldLookup.put(mFields[i], new Integer(i));
		}
	}

	/**
	 * Returns a byte array representation of this tuple. This is used to
	 * determine the natural sort order of tuples, but useful for little else.
	 * 
	 * @return byte array representation of this tuple
	 */
	private byte[] getBytes() {
		if (mBytes == null)
			generateByteRepresentation();

		return mBytes;
	}

	private void generateByteRepresentation() {
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(byteStream);
		try {
			for (int i = 0; i < mFields.length; i++) {
				if (mObjects[i] == null) {
					out.writeUTF(mSymbols[i]);
				} else if (mTypes[i] == Integer.class) {
					out.writeInt((Integer) mObjects[i]);
				} else if (mTypes[i] == Boolean.class) {
					out.writeBoolean((Boolean) mObjects[i]);
				} else if (mTypes[i] == Long.class) {
					out.writeLong((Long) mObjects[i]);
				} else if (mTypes[i] == Float.class) {
					out.writeFloat((Float) mObjects[i]);
				} else if (mTypes[i] == Double.class) {
					out.writeDouble((Double) mObjects[i]);
				} else if (mTypes[i] == String.class) {
					out.writeUTF(mObjects[i].toString());
				} else {
					ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
					DataOutputStream dataOut = new DataOutputStream(bytesOut);

					((Writable) mObjects[i]).write(dataOut);
					out.write(bytesOut.toByteArray());
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		mBytes = byteStream.toByteArray();
	}

	/**
	 * Deserializes the Tuple.
	 * 
	 * @param in
	 *            source for raw byte representation
	 */
	public void readFields(DataInput in) throws IOException {
		int numFields = in.readInt();

		mObjects = new Object[numFields];
		mSymbols = new String[numFields];
		mFields = new String[numFields];
		mTypes = new Class[numFields];

		for (int i = 0; i < numFields; i++) {
			mFields[i] = in.readUTF();
		}

		for (int i = 0; i < numFields; i++) {
			byte type = in.readByte();

			if (type == SYMBOL) {
				String className = in.readUTF();
				try {
					mTypes[i] = Class.forName(className);
				} catch (Exception e) {
					e.printStackTrace();
				}
				mObjects[i] = null;
				mSymbols[i] = in.readUTF();
			} else if (type == INT) {
				mTypes[i] = Integer.class;
				mObjects[i] = in.readInt();
			} else if (type == BOOLEAN) {
				mTypes[i] = Boolean.class;
				mObjects[i] = in.readBoolean();
			} else if (type == LONG) {
				mTypes[i] = Long.class;
				mObjects[i] = in.readLong();
			} else if (type == FLOAT) {
				mTypes[i] = Float.class;
				mObjects[i] = in.readFloat();
			} else if (type == DOUBLE) {
				mTypes[i] = Double.class;
				mObjects[i] = in.readDouble();
			} else if (type == STRING) {
				mTypes[i] = String.class;
				mObjects[i] = in.readUTF();
			} else {
				try {
					String className = in.readUTF();
					mTypes[i] = Class.forName(className);

					int sz = in.readInt();
					byte[] bytes = new byte[sz];
					in.readFully(bytes);

					Writable obj = (Writable) mTypes[i].newInstance();
					obj.readFields(new DataInputStream(
							new ByteArrayInputStream(bytes)));
					mObjects[i] = obj;
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * Serializes this Tuple.
	 * 
	 * @param out
	 *            where to write the raw byte representation
	 */
	public void write(DataOutput out) throws IOException {
		out.writeInt(mFields.length);
		for (int i = 0; i < mFields.length; i++) {
			out.writeUTF(mFields[i]);
		}

		for (int i = 0; i < mFields.length; i++) {
			if (mObjects[i] == null && mSymbols[i] == null) {
				throw new TupleException("Cannot serialize null fields!");
			}

			if (containsSymbol(i)) {
				out.writeByte(SYMBOL);
				out.writeUTF(mTypes[i].getCanonicalName());
				out.writeUTF(mSymbols[i]);
			} else if (mTypes[i] == Integer.class) {
				out.writeByte(INT);
				out.writeInt((Integer) mObjects[i]);
			} else if (mTypes[i] == Boolean.class) {
				out.writeByte(BOOLEAN);
				out.writeBoolean((Boolean) mObjects[i]);
			} else if (mTypes[i] == Long.class) {
				out.writeByte(LONG);
				out.writeLong((Long) mObjects[i]);
			} else if (mTypes[i] == Float.class) {
				out.writeByte(FLOAT);
				out.writeFloat((Float) mObjects[i]);
			} else if (mTypes[i] == Double.class) {
				out.writeByte(DOUBLE);
				out.writeDouble((Double) mObjects[i]);
			} else if (mTypes[i] == String.class) {
				out.writeByte(STRING);
				out.writeUTF(mObjects[i].toString());
			} else {
				out.writeByte(WRITABLE);

				ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
				DataOutputStream dataOut = new DataOutputStream(bytesOut);

				out.writeUTF(mTypes[i].getCanonicalName());
				((Writable) mObjects[i]).write(dataOut);
				out.writeInt(bytesOut.size());
				out.write(bytesOut.toByteArray());
			}
		}
	}

	/**
	 * Generates human-readable String representation of this Tuple.
	 * 
	 * @return human-readable String representation of this Tuple
	 */
	public String toString() {
		StringBuffer sb = new StringBuffer();

		for (int i = 0; i < mFields.length; i++) {
			if (i != 0)
				sb.append(", ");
			if (mSymbols[i] != null) {
				sb.append(mSymbols[i]);
			} else {
				sb.append(mObjects[i]);
			}
		}

		return "(" + sb.toString() + ")";
	}

	/**
	 * Compares this Tuple with the specified object for order. Returns
	 * <code>-1</code>, <code>0</code>, or <code>1</code> as this Tuple
	 * is "less than", "equal to", or "greater than" the specified object. Note
	 * that a raw binary representation is used for sorting, and thus the sort
	 * order does not correspond to the ordering of field values.
	 * 
	 * @return <code>-1</code>, <code>0</code>, or <code>1</code> as
	 *         this Tuple is "less than", "equal to", or "greater than" the
	 *         specified object
	 */
	public int compareTo(Object obj) {
		byte[] thoseBytes = ((Tuple) obj).getBytes();
		byte[] theseBytes = this.getBytes();

		return WritableComparator.compareBytes(theseBytes, 0,
				theseBytes.length, thoseBytes, 0, thoseBytes.length);
	}

	/**
	 * Returns a hash code for this Tuple.
	 * 
	 * @return hash code for this Tuple
	 */
	public int hashCode() {
		if (mBytes == null)
			generateByteRepresentation();

		return WritableComparator.hashBytes(mBytes, mBytes.length);
	}

	/**
	 * A Comparator optimized for {@link Tuple}. Natural sort order is based on
	 * a raw byte representation, and therefore does not correspond to the
	 * natural ordering of field values.
	 */
	public static class Comparator extends WritableComparator {
		/**
		 * Constructs a Comparator for Tuple.
		 */
		public Comparator() {
			super(Tuple.class);
		}

		/**
		 * Compare the raw byte representations of the Tuples.
		 */
		public int compare(byte[] b1, int s1, int l1, byte[] b2, int s2, int l2) {
			return compareBytes(b1, s1, l1, b2, s2, l2);
		}
	}

	// register this comparator
	static {
		WritableComparator.define(Tuple.class, new Comparator());
	}

}
