package me.fluglow;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterOutputStream;

//(https://stackoverflow.com/questions/10572398/how-can-i-easily-compress-and-decompress-strings-to-from-byte-arrays)
enum ByteArrayCompressor {
	;
	public static byte[] compress(byte[] bytes) {
		if(bytes == null) return null;
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		try {
			OutputStream out = new DeflaterOutputStream(outputStream);
			out.write(bytes);
			out.close();
		} catch (IOException e) {
			throw new AssertionError(e);
		}
		return outputStream.toByteArray();
	}

	public static byte[] decompress(byte[] bytes) {
		if(bytes == null) return null;
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		try {
			OutputStream out = new InflaterOutputStream(outStream);
			out.write(bytes);
			out.close();
			return outStream.toByteArray();
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}
}