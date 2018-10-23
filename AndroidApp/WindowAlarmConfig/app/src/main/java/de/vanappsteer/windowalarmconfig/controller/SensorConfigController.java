package de.vanappsteer.windowalarmconfig.controller;

import de.vanappsteer.windowalarmconfig.interfaces.ConfigController;
import de.vanappsteer.windowalarmconfig.interfaces.ConfigView;
import de.vanappsteer.windowalarmconfig.interfaces.SensorConfigView;
import de.vanappsteer.windowalarmconfig.models.SensorConfigModel;

public class SensorConfigController implements ConfigController<SensorConfigModel> {

    private SensorConfigModel mModel;
    private SensorConfigView mView;

    public SensorConfigController(SensorConfigModel model, SensorConfigView view) {
        mModel = model;
        mView = view;
    }

    @Override
    public void updateView() {
        mView.updateSensorPollInterval(mModel.getSensorPollInterval());
    }

    @Override
    public SensorConfigModel getModel() {
        return mModel;
    }


    /* BEGIN GETTER */
    public String getSensorPollInterval() {
        return mModel.getSensorPollInterval();
    }
    /* END GETTER */


    /* BEGIN SETTER */
    public void setSensorPollInterval(String sensorPollInterval) {
        mModel.setSensorPollInterval(sensorPollInterval);
    }
    /* END SETTER */
}
