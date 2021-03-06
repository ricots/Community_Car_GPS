package djamelfel.communitycargps;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.osmdroid.api.IMapController;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.util.constants.MapViewConstants;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public class Maps extends Activity implements MapViewConstants, LocationListener, View
        .OnClickListener {

    private LocationManager lManager;
    private Location location;
    private MapView mapView;
    private IMapController mapController;
    private ToggleButton gpsTButton;
    private Settings settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        lManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);

        mapView = (MapView) findViewById(R.id.mapview);
        mapView.setTileSource(TileSourceFactory.MAPNIK);

        /* Permet de zoomer à l'aide de deux doigts */
        mapView.setBuiltInZoomControls(true);
        mapView.setMultiTouchControls(true);
        mapView.setMinZoomLevel(7);
        mapView.setMaxZoomLevel(30);

        //limiter la carte uniquement a un seul modele
        //BoundingBoxE6 bbox = new BoundingBoxE6(0.0, 100.0, 0.0, 100.0);
        //mapView.setScrollableAreaLimit(bbox);

        mapController = mapView.getController();
        mapController.setZoom(9);

        MapOverlay mmapOverlay = new MapOverlay(this);
        List<Overlay> listOfOverlays = mapView.getOverlays();
        listOfOverlays.add(mmapOverlay);

        gpsTButton = (ToggleButton) findViewById(R.id.gps);
        findViewById(R.id.gps).setOnClickListener(this);
        findViewById(R.id.simulation).setOnClickListener(this);

        Bundle extras = getIntent().getExtras();
        if(extras != null)
            settings = extras.getParcelable("settings");
        else
            settings = new Settings();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_maps, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                Intent intent = new Intent(Maps.this, DisplaySettings.class);
                intent.putExtra("settings", settings);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        this.location = location;

        GeoPoint startPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
        mapController.setCenter(startPoint);
        mapController.setZoom(14);

        mapView.invalidate();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.gps:
                if(gpsTButton.isChecked())
                    enablePosition();
                else
                    disablePosition();
                break;
            case R.id.simulation:
                new UserLoginPostgresql().execute();

                break;
            default:
                break;
        }
    }

    private void enablePosition() {
        lManager.requestLocationUpdates("network", 30000, 0, this);
    }

    private void disablePosition() {
        lManager.removeUpdates(this);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        String newStatus = "";
        switch (status) {
            case LocationProvider.OUT_OF_SERVICE:
                newStatus = "OUT_OF_SERVICE";
                break;
            case LocationProvider.TEMPORARILY_UNAVAILABLE:
                newStatus = "TEMPORARILY_UNAVAILABLE";
                break;
            case LocationProvider.AVAILABLE:
                newStatus = "AVAILABLE";
                break;
        }
        Toast.makeText(this, newStatus, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onProviderEnabled(String provider) {
        String msg = String.format(getResources().getString(R.string.provider_enabled), provider);
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onProviderDisabled(String provider) {
        String msg = String.format(getResources().getString(R.string.provider_disabled), provider);
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    public class MapOverlay extends org.osmdroid.views.overlay.Overlay {
        Paint lp3;
        float pisteX;
        float pisteY;
        Projection projection;
        Point pt;
        GeoPoint gie;
        Rect rec;

        public MapOverlay(Context ctx) {
            super(ctx);

            lp3 = new Paint();
            lp3.setColor(Color.RED);
            lp3.setAntiAlias(true);
            lp3.setStyle(Paint.Style.FILL);
            lp3.setStrokeWidth(1);
            pt = new Point();
        }

        @Override
        protected void draw(Canvas pC, MapView pOsmv, boolean shadow) {
            if (shadow)
                return;

            if(location != null) {
                projection = pOsmv.getProjection();
                gie = new GeoPoint(location.getLatitude(),location.getLongitude());
                rec = mapView.getScreenRect(new Rect());
                projection.toPixels(gie, pt);
                pisteX = pt.x-rec.left;
                pisteY = pt.y - rec.top;
                pC.drawCircle(pisteX, pisteY, 15, lp3);
            }
        }
    }

    /**
     * Represents an asynchronous login/registration task used to authenticate
     * the user.
     */
    public class UserLoginPostgresql extends AsyncTask<Void, Void, Void> {

        Connection conn;
        Statement st;
        ResultSet rs;

        @Override
        protected Void doInBackground(Void... params) {
            try {
                Class.forName("org.postgresql.Driver");
                /**
                 * Adresse IP/user/mot de passe a modifier en statique en fonction de la base de
                 * données
                 */
                String url = "jdbc:postgresql://192.168.0.14:5432/postgres";
                String user = "djamel";
                String password = "bus_can";

                conn = DriverManager.getConnection(url, user, password);

                st = conn.createStatement();
                rs = st.executeQuery("SELECT \"DonneesVoiture\".\"Id\", \"Users\"" +
                        ".\"Prenom\", \"Users\".\"Ville\", \"DonneesVoiture\".\"posx\", " +
                        "\"DonneesVoiture\".\"posy\", \"DonneesVoiture\".\"timestamp\", " +
                        "\"DonneesVoiture\".\"IdentifiantCommande\", \"DonneesVoiture\"" +
                        ".\"Valeur\" FROM \"DonneesVoiture\" inner join \"Users\" on \"DonneesVoiture\"" +
                        ".\"IdUser\" = \"Users\".\"Id\" order by \"Id\" desc limit 1;");
                rs.next();
            }
            catch(Exception e) {
                Log.d("LOG_TAG", "Failure " + e.getMessage() );
                return null;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            LayoutInflater inflater = getLayoutInflater();
            View layout = inflater.inflate(R.layout.toast_layout, (ViewGroup) findViewById(R.id.toast_layout_root));

            ImageView image = (ImageView) layout.findViewById(R.id.image);
            image.setImageDrawable(getResources().getDrawable(getResources().getIdentifier
                    ("@mipmap/ic_notification_bell", null, getPackageName())));

            TextView text = (TextView) layout.findViewById(R.id.text);

            try {
                String message = "L'utilisateur: " + rs.getString(2) + " positionné à " + rs
                        .getString(3) + "(" +rs.getString(4) + "," + rs.getString(5) + ") le " +
                        rs.getString(6) + " à obtenu pour "  + rs.getString(7) +
                        " la valeur suivante " + rs.getString(8);
                text.setText(message);
            } catch (SQLException e) {
                e.printStackTrace();
            }

            Toast toast = new Toast(getApplicationContext());
            toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
            toast.setDuration(Toast.LENGTH_LONG);
            toast.setView(layout);
            toast.show();

            try {
                conn.close();
                st.close();
                rs.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
