package org.droidplanner.android.utils.file;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.Comparator;

public class FileList {

    public static final String WAYPOINT_FILENAME_EXT = ".dpwp";

    public static final String PARAM_FILENAME_EXT = ".param";

	static public String[] getWaypointFileList() {
		FilenameFilter filter = new FilenameFilter() {
			@Override
			public boolean accept(File dir, String filename) {
				return filename.contains(WAYPOINT_FILENAME_EXT);
			}
		};
		return getFileList(DirectoryPath.getWaypointsPath(), filter);
	}

	public static String[] getFavoriteWaypointFileList() {
		FilenameFilter filter = new FilenameFilter() {
			@Override
			public boolean accept(File dir, String filename) {
				return filename.contains(WAYPOINT_FILENAME_EXT);
			}
		};

		return getFileList(DirectoryPath.getFavoriteWaypointsPath(), filter);
	}

	public static String[] getParametersFileList() {
		FilenameFilter filter = new FilenameFilter() {
			@Override
			public boolean accept(File dir, String filename) {
				return filename.contains(PARAM_FILENAME_EXT);
			}
		};
		return getFileList(DirectoryPath.getParametersPath(), filter);
	}

	static public String[] getFileList(String path, FilenameFilter filter) {
		File mPath = new File(path);
		try {
			mPath.mkdirs();
			if (mPath.exists()) {

				final String[] names;
				File[] files = mPath.listFiles(filter);
				if(files != null) {
					Arrays.sort(files, new Comparator<File>() {
						@Override
						public int compare(File lhs, File rhs) {
							return Long.valueOf(rhs.lastModified()).compareTo(Long.valueOf(lhs.lastModified()));
						}
					});

					names = new String[files.length];
					for(int i = 0; i < files.length; ++i) {
						names[i] = files[i].getName();
					}
				}
				else {
					names = mPath.list(filter);
				}

				return names;
			}
		} catch (SecurityException e) {
			e.printStackTrace();
		}
		return new String[0];
	}

}
