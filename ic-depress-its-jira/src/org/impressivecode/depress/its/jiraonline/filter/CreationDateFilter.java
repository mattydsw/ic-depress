package org.impressivecode.depress.its.jiraonline.filter;

import org.impressivecode.depress.its.ITSTimePeriodFilter;
import org.knime.core.node.defaultnodesettings.SettingsModel;

public class CreationDateFilter extends ITSTimePeriodFilter {

    @Override
    public SettingsModel[] getSettingModels() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getName() {
        return "Creation date";
    }
}
