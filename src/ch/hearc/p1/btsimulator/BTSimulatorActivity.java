package ch.hearc.p1.btsimulator;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.EmptyStackException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import ch.hearc.p1.btsimulator.rs232.CrcException;
import ch.hearc.p1.btsimulator.rs232.RS232Command;
import ch.hearc.p1.btsimulator.rs232.RS232CommandType;
import android.app.Activity;
import android.bluetooth.*;
import android.content.Intent;
import android.os.Bundle;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class BTSimulatorActivity extends Activity {
	
	private static final int REQUEST_ENABLE_BT = 1;
	private static final int LIST_DEVICES = 2;

	private BluetoothAdapter bta;
	private BluetoothSocket bts;
	private BluetoothDevice btd;
	
	private OutputStream out;
	private InputStream in;
	private StringBuffer inbuffer = new StringBuffer();
	
	private ConcurrentLinkedQueue<RS232Command> commandQueue = new ConcurrentLinkedQueue<RS232Command>();
	
	
	/**
	 * Represents the direction of the arrow that is displayed on the PIC screen.
	 */
	public enum PicArrowDirection
	{
		NORTH(0), NORTHWEST(1), WEST(2), SOUTHWEST(3), SOUTH(4), SOUTHEAST(5), EAST(6), NORTHEAST(7);
		private int number;
		private PicArrowDirection(int _number)
		{
			number = _number;
		}
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		bta = BluetoothAdapter.getDefaultAdapter();
		if(bta == null) {
			Toast.makeText(getApplicationContext(), "Pas de Bluetooth sur cet appareil !", Toast.LENGTH_LONG).show();
			finish();
		}
		if (!bta.isEnabled()) {
		    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
		    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		}
		else
		{
			Intent serverIntent = new Intent(this, DeviceListActivity.class);
    		startActivityForResult(serverIntent, LIST_DEVICES);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		// TODO Auto-generated method stub
		super.onActivityResult(requestCode, resultCode, data);
		if(requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
			Intent serverIntent = new Intent(this, DeviceListActivity.class);
			startActivityForResult(serverIntent, LIST_DEVICES);
		}
		else if(requestCode == LIST_DEVICES) {
			// When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
                // Get the device MAC address
                String address = data.getExtras()
                                     .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                // Get the BLuetoothDevice object
                btd = bta.getRemoteDevice(address);
                connectAndSetup();
            }
		}
		else {
			Toast.makeText(getApplicationContext(), "Vous devez activer Bluetooth !", Toast.LENGTH_LONG).show();
			finish();
		}
	}
	
	private void connectAndSetup() {
		try {
			bts = btd.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
			bts.connect();
			
			final TextView txt = (TextView) findViewById(R.id.editText1);
			txt.setText("Connecté : " + btd.getName() + "\n\n");

			out = bts.getOutputStream();
			in = bts.getInputStream();
			
			String com = "$" + RS232CommandType.EMPTY.toString() + ",*";
			com += RS232Command.hexToAscii(RS232Command.computeCrc(com));
			out.write(com.getBytes());
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();

			Toast.makeText(getApplicationContext(), "Impossible d'ouvrir le serveur de connexion !", Toast.LENGTH_LONG).show();
			finish();
		}
	}

	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		try {
			bts.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		finish();
	}

}
