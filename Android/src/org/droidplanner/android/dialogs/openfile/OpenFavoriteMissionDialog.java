package org.droidplanner.android.dialogs.openfile;

import org.droidplanner.android.utils.file.IO.FavoriteMissionReader;
import org.droidplanner.android.utils.file.IO.MissionReader;

public abstract class OpenFavoriteMissionDialog extends OpenFileDialog {
	public abstract void waypointFileLoaded(MissionReader reader);

	@Override
	protected FileReader createReader() {
		return new FavoriteMissionReader();
	}

	@Override
	protected void onDataLoaded(FileReader reader) {
		waypointFileLoaded((MissionReader) reader);
	}
}
