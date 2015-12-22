package org.droidplanner.android.utils.file.IO;

import org.droidplanner.android.utils.file.DirectoryPath;
import org.droidplanner.android.utils.file.FileList;

/**
 * Created by kellys on 12/12/15.
 */
public class FavoriteMissionReader extends MissionReader {
    @Override
    public String getPath() {
        return DirectoryPath.getFavoriteWaypointsPath();
    }

    @Override
    public String[] getFileList() {
        return FileList.getFavoriteWaypointFileList();
    }
}
