package tools;

import java.io.File;

public class Folders {

	/**
	 * deletes every file in a folder, and then deletes the folder
	 * 
	 * @param folderToDelete
	 * @return whether the operation was successful
	 */
	public static boolean deleteFolder(File folderToDelete, boolean deleteActualFolder) {
		File[] list = folderToDelete.listFiles();
		boolean isDeleted = false;

		/*
		 * if deleteActualFolder is set to false, we just delete all of the
		 * items in the folder, but not the folder itself
		 */
		try {
			for (File item : list) {
				isDeleted = item.delete();
			}
		} catch (SecurityException err) {
			err.printStackTrace();
		}

		/*
		 * if deleteActualFolder is set to true, we delete the folder along with
		 * all of the files in it
		 */
		if (deleteActualFolder) {
			isDeleted = folderToDelete.delete();
		}

		return isDeleted;
	}
	
	public static boolean deleteFolder(String folderPath, boolean deleteActualFolder) {
		return deleteFolder(new File(folderPath), deleteActualFolder);
	}

}
