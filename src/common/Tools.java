package common;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import org.apache.commons.io.FileUtils;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class Tools {

	public static String GetFileString(String filePath) {
		String fileString = null;
		try {
			fileString = Files.toString(new File(filePath), Charsets.UTF_8);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return fileString;
	}
	
	public static String GetFileString(String filePath, String encoding) {
		String fileString = null;
		try {
			return FileUtils.readFileToString(new File(filePath), encoding);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return fileString;
	}
}
