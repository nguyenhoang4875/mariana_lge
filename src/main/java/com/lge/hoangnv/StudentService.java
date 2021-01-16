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

    private HandlerThread handlerThread = new HandlerThread("handle_thread_in_student");
    private Handler handler;

    // receive form EUC
    private ICoreService iCoreService;
    private IPropertyService iPropertyService;
    private IConfigurationService iConfigurationService;

    private TestCapability testCapability;

    private boolean failedClick = false;
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
        Log.d(TAG, "onCreate: xxxxxxxxxxxxxxxxxxxxxxxxxx");
        bindCoreService();
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper()) {
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
        urRegisterProperty();
        unbindService(coreServiceConnection);
        handlerThread.quit();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind in Student Service ");
        return sBinder;
    }


    private Binder sBinder = new IStudentInterface.Stub() {
        @Override
        public void registerListener(IHMIListener listener) throws RemoteException {
            Log.d(TAG, "registerListener run: ");
            ihmiListener = listener;
        }

        @Override
        public void unregisterListener(IHMIListener listener) throws RemoteException {
            Log.d(TAG, "unregisterListener: ");
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
            if (testCapability.isDistanceSupported()) return;
            PropertyEvent propertyDistanceEvent = new PropertyEvent(IPropertyService.PROP_DISTANCE_UNIT, PropertyEvent.STATUS_AVAILABLE, unit);
            iPropertyService.setProperty(propertyDistanceEvent);
        }

        @Override
        public void setConsumptionUnit(int unit) throws RemoteException {
            if (!testCapability.isConsumptionSupported()) return;
            PropertyEvent propertyConsumptionEven = new PropertyEvent(IPropertyService.PROP_CONSUMPTION_UNIT, PropertyEvent.STATUS_AVAILABLE, unit);
            iPropertyService.setProperty(propertyConsumptionEven);
        }

        @Override
        public void resetData() throws RemoteException {
            failedClick = false;
            PropertyEvent propertyResetDataEvent = new PropertyEvent(IPropertyService.PROP_RESET, PropertyEvent.STATUS_AVAILABLE, true);
            iPropertyService.setProperty(propertyResetDataEvent);
            if (ihmiListener != null) {
                firstUnit = true;
            }
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
            if (iCoreService != null) {
                try {
                    iPropertyService = iCoreService.getPropertyService();
                    iConfigurationService = iCoreService.getConfigurationService();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                // register property
                registerProperty();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    private void handleEventMessage(Message msg) {
        PropertyEvent propertyEvent = (PropertyEvent) msg.obj;
        Log.d(TAG, "------------------------------------------------------------");
        Log.d(TAG, "Message msg arg1: " + msg.arg1);
        Log.d(TAG, "Message msg arg2: " + msg.arg2);
        Log.d(TAG, "Message what: " + msg.what);
        Log.d(TAG, "Property Event Id: " + propertyEvent.getPropertyId());
        Log.d(TAG, "Property Event Status: " + propertyEvent.getStatus());
        Log.d(TAG, "Property Event Value: " + propertyEvent.getValue());
        Log.d(TAG, "------------------------------------------------------------");
        if (msg.what == MESSAGE_EVENT_REGISTER_LISTENER) {
            if (propertyEvent != null) {
                if (ihmiListener != null) {
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
                            Log.d(TAG, "Property distan vaule Value: " + propertyEvent.getValue());
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
                            if (failedClick) break;
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
                            if (failedClick) break;
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
                            Handler handler = new Handler();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        ihmiListener.onError(false);
                                    } catch (RemoteException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }, 1000);
                            break;

                        default:
                            break;
                    }
                }
            }
        } else if (msg.what == MESSAGE_EVENT_UNREGISTER_LISTENER) {

        }
    }

    private void registerProperty() {
        if (iPropertyService != null) {
            try {
                iPropertyService.registerListener(IPropertyService.PROP_DISTANCE_UNIT, eventRegister);
                iPropertyService.registerListener(IPropertyService.PROP_DISTANCE_VALUE, eventRegister);
                iPropertyService.registerListener(IPropertyService.PROP_CONSUMPTION_UNIT, eventRegister);
                iPropertyService.registerListener(IPropertyService.PROP_CONSUMPTION_VALUE, eventRegister);
                iPropertyService.registerListener(IPropertyService.PROP_RESET, eventRegister);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private void urRegisterProperty() {
        if (iPropertyService != null) {
            try {
                iPropertyService.registerListener(IPropertyService.PROP_DISTANCE_UNIT, eventUnRegister);
                iPropertyService.registerListener(IPropertyService.PROP_DISTANCE_VALUE, eventUnRegister);
                iPropertyService.registerListener(IPropertyService.PROP_CONSUMPTION_UNIT, eventUnRegister);
                iPropertyService.registerListener(IPropertyService.PROP_CONSUMPTION_VALUE, eventUnRegister);
                iPropertyService.registerListener(IPropertyService.PROP_RESET, eventUnRegister);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private IPropertyEventListener.Stub eventRegister = new IPropertyEventListener.Stub() {

        @Override
        public void onEvent(PropertyEvent event) throws RemoteException {
            Message message = Message.obtain();
            message.what = MESSAGE_EVENT_REGISTER_LISTENER;
            message.obj = event;
            handler.sendMessage(message);
        }
    };

    private IPropertyEventListener.Stub eventUnRegister = new IPropertyEventListener.Stub() {

        @Override
        public void onEvent(PropertyEvent event) throws RemoteException {
            Message message = Message.obtain();
            message.what = MESSAGE_EVENT_UNREGISTER_LISTENER;
            message.obj = event;
            handler.sendMessage(message);
        }
    };

}