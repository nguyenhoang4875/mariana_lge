// IPropertyEventListener.aidl
package dcv.test.servicecore;

import dcv.test.servicecore.PropertyEvent;

interface IPropertyEventListener {
    oneway void onEvent(in PropertyEvent event);
}
