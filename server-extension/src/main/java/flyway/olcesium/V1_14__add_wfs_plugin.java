package flyway.olcesium;

import fi.nls.oskari.domain.map.view.Bundle;
import fi.nls.oskari.domain.map.view.View;
import fi.nls.oskari.log.LogFactory;
import fi.nls.oskari.log.Logger;
import fi.nls.oskari.map.view.AppSetupServiceMybatisImpl;
import fi.nls.oskari.map.view.ViewException;
import fi.nls.oskari.map.view.ViewService;
import fi.nls.oskari.util.JSONHelper;
import org.flywaydb.core.api.migration.jdbc.JdbcMigration;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class V1_14__add_wfs_plugin implements JdbcMigration {

    private static final Logger LOG = LogFactory.getLogger(V1_14__add_wfs_plugin.class);
    private static final String CESIUM_VIEW_NAME = "Geoportal Ol Cesium";
    private static final String MAP_BUNDLE_NAME = "mapfull";
    private static final String WFS_PLUGIN_ID = "Oskari.wfsvector.WfsVectorLayerPlugin";

    private ViewService viewService = null;

    public void migrate(Connection connection) {
        viewService =  new AppSetupServiceMybatisImpl();
        updateCesiumViews(connection);
    }

    private void updateCesiumViews(Connection connection) {
        try {
            List<Long> viewIds = getCesiumViewIds(connection);
            LOG.info("Updating Cesium views - count:", viewIds.size());

            for(Long viewId : viewIds) {
                View modifyView = viewService.getViewWithConf(viewId);
                updateMapFullBundle(modifyView);
            }
        } catch (Exception e) {
            LOG.error(e, "Could not update 3D view");
            throw new RuntimeException(e);
        }
    }

    private void updateMapFullBundle(View view) throws JSONException, ViewException {
        Bundle mapfull = view.getBundleByName(MAP_BUNDLE_NAME);
        JSONObject config = mapfull.getConfigJSON();
        JSONArray plugins = config.getJSONArray("plugins");
        for (int i = 0; i < plugins.length(); i++) {
            if (plugins.getJSONObject(i).get("id").equals(WFS_PLUGIN_ID)) {
                return;
            }
        }
        JSONObject plugin = JSONHelper.createJSONObject("id", WFS_PLUGIN_ID);
        plugins.put(plugin);
        mapfull.setConfig(config.toString());
        viewService.updateBundleSettingsForView(view.getId(), mapfull);
    }

    private List<Long> getCesiumViewIds(Connection conn) throws SQLException {
        List<Long> list = new ArrayList<>();
        final String sql = "SELECT id FROM portti_view WHERE name=?";

        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, CESIUM_VIEW_NAME);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getLong("id"));
                }
            }
        } catch (Exception e) {
            LOG.error(e, "Error getting Cesium portti views");
        }
        return list;
    }
}
