package ch.hearc.p1.btsimulator;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
			connectAndSetup();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		// TODO Auto-generated method stub
		super.onActivityResult(requestCode, resultCode, data);
		if(requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK)
			connectAndSetup();
		else {
			Toast.makeText(getApplicationContext(), "Vous devez activer Bluetooth !", Toast.LENGTH_LONG).show();
			finish();
		}
	}
	
	private void connectAndSetup() {
		try {
			btd = bta.getRemoteDevice("CC:AF:78:EC:E6:01");
			btd.fetchUuidsWithSdp();
			bts = btd.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
			bts.connect();
			
			final TextView txt = (TextView) findViewById(R.id.editText1);
			txt.setText("Connecté : " + btd.getName() + "\n\n");

			out = bts.getOutputStream();
			in = bts.getInputStream();
			
			new Timer("input", true).schedule(new TimerTask() {
				
				@Override
				public void run() {
					// TODO Auto-generated method stub
					int tmp;
					try {
						while((tmp = in.read()) != -1) {
							inbuffer.append((char)tmp);
						}
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
					while((tmp = inbuffer.indexOf("\r\n")) != -1) {
						try {
							commandQueue.add(new RS232Command(inbuffer.substring(0, tmp + 2)));
							inbuffer.delete(0, tmp + 2);
						} catch (IllegalArgumentException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (CrcException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
					
					
					while(!commandQueue.isEmpty()) {
						RS232Command com = commandQueue.poll();
						if (com.getCommandNumber() == RS232CommandType.EMPTY) {
							try {
								out.write("$00,*22\r\n".getBytes());
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					}
				}
			}, 200, 500);
			
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
	}

}
