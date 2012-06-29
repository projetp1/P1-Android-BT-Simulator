package ch.hearc.p1.btsimulator;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import ch.hearc.p1.btsimulator.rs232.CrcException;
import ch.hearc.p1.btsimulator.rs232.RS232Command;
import ch.hearc.p1.btsimulator.rs232.RS232CommandType;

public class BTSimulatorActivity extends Activity implements SensorEventListener {
	
	private static final int REQUEST_ENABLE_BT = 1;
	private static final int LIST_DEVICES = 2;

	private BluetoothAdapter bta;
	private BluetoothSocket bts;
	private BluetoothDevice btd;
	
	private ConnectedThread cth;
	private OutputStream out;
	public StringBuffer inbuffer = new StringBuffer();
	
	
	/**
	 * Represents the direction of the arrow that is displayed on the PIC screen.
	 */
	public enum PicArrowDirection
	{
		NORTH(0), NORTHWEST(1), WEST(2), SOUTHWEST(3), SOUTH(4), SOUTHEAST(5), EAST(6), NORTHEAST(7);
		private int number;
		private PicArrowDirection(int num) {
			number = num;
		}
		
		public int getDrawableId() {
			switch (this) {
				case EAST:
					return R.drawable.e;
				case NORTH:
					return R.drawable.n;
				case NORTHEAST:
					return R.drawable.ne;
				case NORTHWEST:
					return R.drawable.nw;
				case SOUTH:
					return R.drawable.s;
				case SOUTHEAST:
					return R.drawable.se;
				case SOUTHWEST:
					return R.drawable.sw;
				case WEST:
					return R.drawable.w;
				default:
					return 0;
			}
		}
		
		public static PicArrowDirection valueOfByNum(int num) {
			for (PicArrowDirection pad : PicArrowDirection.values()) {
				if(pad.number == num)
					return pad;
			}
			return null;
		}
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {

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
			
			final TextView txt = (TextView) findViewById(R.id.connected);
			txt.setText("Connecté à " + btd.getName());

			out = bts.getOutputStream();
			bts.getInputStream();
			
			cth = new ConnectedThread(bts);
			cth.start();
			
		} catch (IOException e) {
			e.printStackTrace();

			Toast.makeText(getApplicationContext(), "Impossible d'ouvrir la connexion !", Toast.LENGTH_LONG).show();
			finish();
		}
	}
	
	public void send(View view) {
		if(view.getId() == R.id.send)
			send();
	}
	
	private void send() {
		SensorManager smg = (SensorManager)getSystemService(SENSOR_SERVICE);
		
		Sensor acc = smg.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		if(acc == null) {
			Toast.makeText(getApplicationContext(), "Pas d'accéléromètre sur cet appareil !", Toast.LENGTH_LONG);
			finish();
			return;
		}
		smg.registerListener(this, acc, SensorManager.SENSOR_DELAY_NORMAL);
		
		Sensor mag = smg.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
		if(mag == null) {
			Toast.makeText(getApplicationContext(), "Pas de magnétomètre sur cet appareil !", Toast.LENGTH_LONG);
			finish();
			return;
		}
		smg.registerListener(this, mag, SensorManager.SENSOR_DELAY_NORMAL);
		
		LocationManager lma = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
		Location loc = lma.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
		
		Log.d("BTSIM", String.valueOf(loc.getLatitude()) + "," + String.valueOf(loc.getLongitude()));
		
		double latitude = loc.getLatitude();
		char hemns;
		if(latitude < 0) {
			latitude = -latitude;
			hemns = 'S';
		}
		else
			hemns = 'N';
		int lat = (int)latitude;
		double latmin = ((latitude - (double)lat) / 100.0 ) * 6000.0;
		double convertedlat = (double)(lat * 100) + latmin;
		
		double longitude = loc.getLongitude();
		char hemew;
		if(longitude < 0) {
			latitude = -latitude;
			hemew = 'W';
		}
		else
			hemew = 'E';
		int lon = (int)longitude;
		double lonmin = ((longitude - (double)lon) / 100.0 ) * 6000.0;
		double convertedlon = (double)(lon * 100) + lonmin;

		String data = String.valueOf(convertedlat) + "," + hemns + "," + String.valueOf(convertedlon) + "," + hemew;
		RS232Command loccom = new RS232Command(RS232CommandType.LOCATION_UPDATE, data);
		try {
			sendFrame(loccom);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void stopsending(View view) {
		if(view.getId() != R.id.stop)
			return;
		
		SensorManager smg = (SensorManager)getSystemService(SENSOR_SERVICE);
		smg.unregisterListener(this);
	}
	
	private void sendFrame(RS232Command com) throws IOException {
		String command = com.getCommandNumber().toString() + "," + com.getDatas();
		command = "$" + command + "*" + (RS232Command.hexToAscii(RS232Command.computeCrc(command)) + "\r\n");
		try {
			out.write(command.getBytes());
		} catch (IOException e) {
			if(e.getMessage().equalsIgnoreCase("socket closed"))
				finish();
			else
				throw e;
		}
	}
	
	// The Handler that gets information back from the BluetoothChatService
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if(msg.what == 0) {
            	for (byte b : (byte[])msg.obj) {
					if(b!=0)
						inbuffer.append((char)b);
				}
            	
            	int pos;

        		while ((pos = inbuffer.indexOf("\r\n")) != -1)
        		{
        			RS232Command com;
        			String chain = inbuffer.substring(0, pos + 2);
        			inbuffer.delete(0, pos + 2);
        			try
        			{
        				com = new RS232Command(chain);
        				switch (com.getCommandNumber()) {
						case ACCELEROMETER_UPDATE:
						case LOCATION_UPDATE:
						case MAGNETOMETER_UPDATE:
							send();
							break;
						case CHANGE_TO_ARROW_MODE:
							((ImageView)findViewById(R.id.arrow)).setVisibility(View.VISIBLE);
							PicArrowDirection pad = PicArrowDirection.valueOfByNum(Integer.parseInt(com.getDatas()));
							((ImageView)findViewById(R.id.arrow)).setImageResource(pad.getDrawableId());
							break;
						case CHANGE_TO_POINT_MODE:
							((ImageView)findViewById(R.id.arrow)).setVisibility(View.INVISIBLE);
							break;
						case EMPTY:
							sendFrame(new RS232Command(RS232CommandType.EMPTY, ""));
							break;
						default:
							break;
        				}
        			}
        			catch (CrcException e1)
        			{
        				Log.i("BTSIM", "Frame corrupted");
        			}
        			catch (Exception e1)
        			{
        				Log.w("BTSIM", "Exception while trying to decode frame : " + e1.getMessage());
        			}
        		}
            }
        }
    };

	@Override
	protected void onDestroy() {
		super.onDestroy();
		try {
			bts.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		finish();
	}

	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
		SensorManager smg = (SensorManager)getSystemService(SENSOR_SERVICE);
		smg.unregisterListener(this);
	}

	public void onSensorChanged(SensorEvent event) {
		if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
			((TextView)findViewById(R.id.acc)).setText(event.values[0] + "," + event.values[1] + "," + event.values[2]);
			
			double x = event.values[0];
			double y = event.values[1];
			double z = - event.values[2];
			
			//Conversion
			x /= 9.81;
			y /= 9.81;
			z /= 9.81;
			
			// x, y et z sont en g
			// 2g = 16384
			x *= 16384.0;
			y *= 16384.0;
			z *= 16384.0;
			
			//On ne veut pas de valeurs négatives :
			x += 32768.0;
			y += 32768.0;
			z += 32768.0;
			
			int ix, iy, iz;
			ix = (int)x;
			iy = (int)y;
			iz = (int)z;
			
			//Contrôle des bornes
			ix = (ix <= 0) ? 0 : ix;
			iy = (iy <= 0) ? 0 : iy;
			iz = (iz <= 0) ? 0 : iz;

			ix = (ix >= 65535) ? 65535 : ix;
			iy = (iy >= 65535) ? 65535 : iy;
			iz = (iz >= 65535) ? 65535 : iz;
			
			
			String data = ix + "," + iy + "," + iz;
			try {
				sendFrame(new RS232Command(RS232CommandType.ACCELEROMETER_UPDATE, data));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		else if(event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
			((TextView)findViewById(R.id.mag)).setText(event.values[0] + "," + event.values[1] + "," + event.values[2]);
			
			// Le champ va du Nord au Sud, il faut prendre le contraire. De plus, il faut inverser l'axe z.
			double x = - event.values[0];
			double y = - event.values[1];
			double z = event.values[2];
			
			// Le champ va de -200 à 200 uT
			x /= 200.0;
			y /= 200.0;
			z /= 200.0;
			
			// x, y et z sont en g
			// 2g = 16384
			x *= 32768.0;
			y *= 32768.0;
			z *= 32768.0;
			
			//On ne veut pas de valeurs négatives :
			x += 32768.0;
			y += 32768.0;
			z += 32768.0;
			
			int ix, iy, iz;
			ix = (int)x;
			iy = (int)y;
			iz = (int)z;
			
			//Contrôle des bornes
			ix = (ix <= 0) ? 0 : ix;
			iy = (iy <= 0) ? 0 : iy;
			iz = (iz <= 0) ? 0 : iz;

			ix = (ix >= 65535) ? 65535 : ix;
			iy = (iy >= 65535) ? 65535 : iy;
			iz = (iz >= 65535) ? 65535 : iz;
			
			
			String data = ix + "," + iy + "," + iz;
			try {
				sendFrame(new RS232Command(RS232CommandType.MAGNETOMETER_UPDATE, data));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		else
			Log.w("BTSIM", "Unknown Sensor : "+ event.sensor.getName());
	}

	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// Nothing to do
	}
	
	/**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final InputStream mmInStream;

        public ConnectedThread(BluetoothSocket socket) {
            InputStream tmpIn = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
            } catch (IOException e) {
                Log.e("BTSIMCT", "temp sockets not created", e);
            }

            mmInStream = tmpIn;
        }

        public void run() {

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                	byte[] buffer = new byte[256];
                    int bytes;
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);

                    // Send the obtained bytes to the UI Activity
                    if(bytes > 0)
                    	mHandler.obtainMessage(0, bytes, -1, buffer)
                            .sendToTarget();
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }
    }

}
