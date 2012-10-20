package com.alarmworkflow.app;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;
import android.widget.Toast;

public class MainActivity extends Activity {

	private static final int FETCH_OPERATIONS_TIMEOUT = 10; 
    private static final int SERVICE_MAXAGE = 7;
    
    private boolean _isFetchingOperations;
	
	private ListView _lsvOperations;
	
	private OperationsAdapter _adapter;
	private List<Operation> _adapterList;

	public MainActivity() {

	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		PreferenceManager.setDefaultValues(this, R.xml.activity_preferences, false);
		
		// Retrieve controls from layout
		_lsvOperations = (ListView) findViewById(R.id.lsvOperations);
		
		// Load the persisted cache
		try {
			OperationCache.getInstance().loadPersistedCache(openFileInput(OperationCache.PERSISTENT_STORAGE_FILENAME));
		} catch (FileNotFoundException e) {
			// It's OK if the file does not exist --> ignore exception
		}
		
		// Now create a new adapter for our list view and host the data in it
		_adapterList = new ArrayList<Operation>();
		_adapter = new OperationsAdapter(getApplicationContext(), R.layout.operationitemlayout, _adapterList);
		_lsvOperations.setAdapter(_adapter);
		
		// Load in all operations if there are any
		for (Operation operation : OperationCache.getInstance().getRecentOperations(SERVICE_MAXAGE, 5)) {
			checkAndAddOperationToList(operation);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {
			case R.id.menu_refresh:
				refreshOperationsList();
				return true;
			case R.id.menu_settings:
				Intent intent = new Intent(MainActivity.this, SettingsPreferenceActivity.class);
			    startActivity(intent);
				return true;

			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
        
        // Persist operation cache
        try {
			OperationCache.getInstance().persistCache(openFileOutput(OperationCache.PERSISTENT_STORAGE_FILENAME, MODE_PRIVATE));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
	}
	
	private boolean isNetworkAvailable(boolean showToastIfUnavailable){
		ConnectivityManager cm = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
		 
		NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
		boolean isConnected = activeNetwork.isConnectedOrConnecting();
		if(!isConnected && showToastIfUnavailable){
			Toast.makeText(this, R.string.toast_no_network_connection, Toast.LENGTH_LONG).show();
		}
		return isConnected;
	}
	
	private void refreshOperationsList(){
		// Check if we have network connection.
		if(!isNetworkAvailable(true)){
			return;
		}
		// If the process is already running
		if (_isFetchingOperations){
			return;
		}
		
		// Execute the fetching in a separate thread to avoid unlimited wait
		FetchOperationsTask task = new FetchOperationsTask(this);
		// The task doesn't need a parameter so we just give zero
		task.execute(0);
		// Mark the process as busy
		_isFetchingOperations = true;
		// Constrain the task duration to not wait indefinitely
		try {
			task.get((long)FETCH_OPERATIONS_TIMEOUT, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TimeoutException e) {
			Toast.makeText(this, R.string.toast_fetching_operations_timed_out, Toast.LENGTH_LONG).show();
		}
		
		_isFetchingOperations = false;
	}
	
	/**
	 * Adds an operation to the list in case it doesn't exist yet, and updates the UI.
	 * @param operation The operation to check and add.
	 */
	public void checkAndAddOperationToList(Operation operation){
		if (operation == null){
			return;
		}

		if (_adapterList.contains(operation)){
			return;
		}
		
		// Add the operation to the adapter and notify
		_adapter.add(operation);		
		_adapter.notifyDataSetChanged();
	}

}