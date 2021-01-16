// IPropertyService.aidl
package dcv.test.servicecore;

import dcv.test.servicecore.IPropertyEventListener;
import dcv.test.servicecore.PropertyEvent;
// Declare any non-default types here with import statements

interface IPropertyService {
    const int PROP_DISTANCE_UNIT = 1;
    const int PROP_DISTANCE_VALUE = 2;
    const int PROP_CONSUMPTION_UNIT = 3;
    const int PROP_CONSUMPTION_VALUE = 4;
    const int PROP_RESET = 5;
    const int KM = 0;
    const int MILE = 1;
    const int L_PER_100KM = 0;
    const int KM_PER_L = 1;

    oneway void registerListener(int propID, in IPropertyEventListener callback);
    oneway void unregisterListener(int propID, in IPropertyEventListener callback);
    oneway void setProperty(in PropertyEvent value);
}
