package com.lge.hoangnv;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;

import dcv.test.servicecore.IConfigurationService;
import dcv.test.servicecore.ICoreService;
import dcv.test.servicecore.IPropertyEventListener;
import dcv.test.servicecore.IPropertyService;
import dcv.test.servicecore.PropertyEvent;
import dcv.test.testhmiapplication.IHMIListener;
import dcv.test.testhmiapplication.IStudentInterface;
import dcv.test.testhmiapplication.TestCapability;

public class StudentService extends Service {

    public static final int MESSAGE_EVENT_REGISTER_LISTENER = 0;
    public static final int MESSAGE_EVENT_UNREGISTER_LISTENER = 1;
    public static final int NUMBER_OF_SIGNAL_PER_MINUTE = 4; // 500ms/1signal => 1 minute 120 signals

    // get form IHM
    private IHMIListener ihmiListener;
    private TestCapability testCapability;


    // receive form EUC
    private ICoreService iCoreService;
    private IPropertyService iPropertyService;
    private IConfigurationService iConfigurationService;

    private HandlerThread handlerThreadMessage = new HandlerThread("handle_thread_for_message");
    private Handler handlerMessage;
    private Handler handlerSleep = new Handler();


    private boolean firstUnit = true;
    private int numberSignal;

    private double[] consumptionList;
    private double currentConsumption;

    // set tag for log
    private static final String TAG = "hoang";
    private static final String CORE_SERVICE_ACTION = "dcv.finaltest.BIND";
    private static final String CORE_SERVICE_PACKAGE = "dcv.test.servicecore";


    public StudentService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: ");
        bindCoreService();
        handlerThreadMessage.start();
        handlerMessage = new Handler(handlerThreadMessage.getLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);
                handleEventMessage(msg);
            }
        };

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "*********************************************************************");
        Log.d(TAG, "onDestroy: Student Service");
        Log.d(TAG, "*********************************************************************");
        unbindService(coreServiceConnection);
        handlerThreadMessage.quit();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind in Student Service ");
        return studentServiceBinder;
    }


    private Binder studentServiceBinder = new IStudentInterface.Stub() {
        @Override
        public void registerListener(IHMIListener listener) throws RemoteException {
            Log.d(TAG, "registerListener run: ");
            ihmiListener = listener;
            registerProperty();
        }

        @Override
        public void unregisterListener(IHMIListener listener) throws RemoteException {
            Log.d(TAG, "unregisterListener: ");
            unRegisterProperty();
            ihmiListener = null;
        }

        @Override
        public TestCapability getCapability() throws RemoteException {
            Log.d(TAG, "getCapability: ");
            if (iConfigurationService != null) {
                testCapability = new TestCapability(
                        iConfigurationService.isSupport(IConfigurationService.CONFIG_DISTANCE),
                        iConfigurationService.isSupport(IConfigurationService.CONFIG_CONSUMPTION));
                return testCapability;
            }
            Log.d(TAG, "Can not connect to ConfigurationService");
            return null;
        }

        @Override
        public void setDistanceUnit(int unit) throws RemoteException {
            Log.d(TAG, "setDistanceUnit: ");
            PropertyEvent propertyDistanceEvent = new PropertyEvent(IPropertyService.PROP_DISTANCE_UNIT, PropertyEvent.STATUS_AVAILABLE, unit);
            iPropertyService.setProperty(propertyDistanceEvent);
        }

        @Override
        public void setConsumptionUnit(int unit) throws RemoteException {
            Log.d(TAG, "setConsumptionUnit: ");
            PropertyEvent propertyConsumptionEven = new PropertyEvent(IPropertyService.PROP_CONSUMPTION_UNIT, PropertyEvent.STATUS_AVAILABLE, unit);
            iPropertyService.setProperty(propertyConsumptionEven);
        }

        @Override
        public void resetData() throws RemoteException {
            Log.d(TAG, "resetData: ");
            PropertyEvent propertyResetDataEvent = new PropertyEvent(IPropertyService.PROP_RESET, PropertyEvent.STATUS_AVAILABLE, true);
            iPropertyService.setProperty(propertyResetDataEvent);
            // rest chart consumption value change
            firstUnit = true;
        }
    };

    private void bindCoreService() {
        Log.d(TAG, "bindCorService: in Student Service");
        Intent intent = new Intent();
        intent.setAction(CORE_SERVICE_ACTION);
        intent.setPackage(CORE_SERVICE_PACKAGE);
        bindService(intent, coreServiceConnection, BIND_AUTO_CREATE);
    }

    private final ServiceConnection coreServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected: coreServiceConnection");
            iCoreService = ICoreService.Stub.asInterface(service);
            if (iCoreService == null) return;
            try {
                iPropertyService = iCoreService.getPropertyService();
                iConfigurationService = iCoreService.getConfigurationService();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "*********************************************************************");
            unRegisterProperty();
            Log.d(TAG, "*********************************************************************");
            Log.d(TAG, "onServiceDisconnected: coreServiceConnection");

        }
    };

    private void handleEventMessage(Message msg) {
        if (ihmiListener == null) return;
        if (msg.what == MESSAGE_EVENT_REGISTER_LISTENER) {
            PropertyEvent propertyEvent = (PropertyEvent) msg.obj;
            if (propertyEvent == null) return;
            if (propertyEvent.getStatus() == PropertyEvent.STATUS_AVAILABLE) {
                switch (propertyEvent.getPropertyId()) {
                    case IPropertyService.PROP_DISTANCE_UNIT:
                        Log.d(TAG, "------------------------------------------------------------");
                        Log.d(TAG, "on distance unit ");
                        Log.d(TAG, "Property distance unit Value: " + propertyEvent.getValue());
                        Log.d(TAG, "------------------------------------------------------------");
                        try {
                            ihmiListener.onDistanceUnitChanged((Integer) propertyEvent.getValue());
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                        break;

                    case IPropertyService.PROP_DISTANCE_VALUE:
                        Log.d(TAG, "------------------------------------------------------------");
                        Log.d(TAG, "on distance value");
                        Log.d(TAG, "Property distance value Value: " + propertyEvent.getValue());
                        Log.d(TAG, "------------------------------------------------------------");
                        try {
                            ihmiListener.onDistanceChanged((Double) propertyEvent.getValue());
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                        break;

                    case IPropertyService.PROP_CONSUMPTION_UNIT:
                        Log.d(TAG, "------------------------------------------------------------");
                        Log.d(TAG, "on consumption unit ");
                        Log.d(TAG, "Property consumption unit Value: " + propertyEvent.getValue());
                        Log.d(TAG, "------------------------------------------------------------");
                        try {
                            ihmiListener.OnConsumptionUnitChanged((Integer) propertyEvent.getValue());
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                        break;

                    case IPropertyService.PROP_CONSUMPTION_VALUE:
                        Log.d(TAG, "------------------------------------------------------------");
                        Log.d(TAG, "on consumption value");
                        Log.d(TAG, "Property consumption value Value: " + propertyEvent.getValue());
                        Log.d(TAG, "------------------------------------------------------------");
                        if (firstUnit) {
                            try {
                                consumptionList = new double[15];
                                ihmiListener.onConsumptionChanged(consumptionList);
                                firstUnit = false;
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                            break;
                        }
                        numberSignal++;
                        currentConsumption += (double) propertyEvent.getValue();
                        if (numberSignal == NUMBER_OF_SIGNAL_PER_MINUTE) {
                            currentConsumption = currentConsumption / NUMBER_OF_SIGNAL_PER_MINUTE;
                            for (int i = 0; i < 14; i++) {
                                consumptionList[i] = consumptionList[i + 1];
                            }
                            consumptionList[14] = currentConsumption;
                            try {
                                ihmiListener.onConsumptionChanged(consumptionList);
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                            numberSignal = 0;
                            currentConsumption = 0;
                        }
                        break;

                    case IPropertyService.PROP_RESET:
                        Log.d(TAG, "------------------------------------------------------------");
                        Log.d(TAG, "on reset");
                        Log.d(TAG, "Property reset Value: " + propertyEvent.getValue());
                        Log.d(TAG, "------------------------------------------------------------");
                        try {
                            ihmiListener.onError(true);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                        sleep(handlerSleep, 1000);
                        break;

                    default:
                        break;
                }
            }
        } else if (msg.what == MESSAGE_EVENT_UNREGISTER_LISTENER) {
            unRegisterProperty();
        }
    }

    private void registerProperty() {
        Log.d(TAG, "registerProperty testCapability:  " + testCapability);
        if (iPropertyService == null) return;
        try {
            if (testCapability.isDistanceSupported()) {
                iPropertyService.registerListener(IPropertyService.PROP_DISTANCE_UNIT, eventRegister);
                iPropertyService.registerListener(IPropertyService.PROP_DISTANCE_VALUE, eventRegister);
            }
            if (testCapability.isConsumptionSupported()) {
                iPropertyService.registerListener(IPropertyService.PROP_CONSUMPTION_UNIT, eventRegister);
                iPropertyService.registerListener(IPropertyService.PROP_CONSUMPTION_VALUE, eventRegister);
            }
            iPropertyService.registerListener(IPropertyService.PROP_RESET, eventRegister);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void unRegisterProperty() {
        if (iPropertyService == null) return;
        try {
            if (testCapability.isDistanceSupported()) {
                iPropertyService.registerListener(IPropertyService.PROP_DISTANCE_UNIT, eventUnRegister);
                iPropertyService.registerListener(IPropertyService.PROP_DISTANCE_VALUE, eventUnRegister);
            }
            if (testCapability.isConsumptionSupported()) {
                iPropertyService.registerListener(IPropertyService.PROP_CONSUMPTION_UNIT, eventUnRegister);
                iPropertyService.registerListener(IPropertyService.PROP_CONSUMPTION_VALUE, eventUnRegister);
            }
            iPropertyService.registerListener(IPropertyService.PROP_RESET, eventUnRegister);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

    }

    private IPropertyEventListener.Stub eventRegister = new IPropertyEventListener.Stub() {

        @Override
        public void onEvent(PropertyEvent event) throws RemoteException {
            Message message = Message.obtain();
            message.what = MESSAGE_EVENT_REGISTER_LISTENER;
            message.obj = event;
            handlerMessage.sendMessage(message);
        }
    };

    private IPropertyEventListener.Stub eventUnRegister = new IPropertyEventListener.Stub() {

        @Override
        public void onEvent(PropertyEvent event) throws RemoteException {
            Message message = Message.obtain();
            message.what = MESSAGE_EVENT_UNREGISTER_LISTENER;
            message.obj = event;
            handlerMessage.sendMessage(message);
        }
    };

    private void sleep(Handler handler, int millisecond) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    ihmiListener.onError(false);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }, millisecond);

    }

}