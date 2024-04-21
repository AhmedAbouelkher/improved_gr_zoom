package com.webcare.zoom;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import us.zoom.sdk.InMeetingService;
import us.zoom.sdk.JoinMeetingOptions;
import us.zoom.sdk.JoinMeetingParams;
import us.zoom.sdk.MeetingError;
import us.zoom.sdk.MeetingService;
import us.zoom.sdk.MeetingStatus;
import us.zoom.sdk.StartMeetingOptions;
import us.zoom.sdk.StartMeetingParamsWithoutLogin;
import us.zoom.sdk.ZoomError;
import us.zoom.sdk.ZoomSDK;
import us.zoom.sdk.ZoomSDKAuthenticationListener;
import us.zoom.sdk.ZoomSDKInitParams;
import us.zoom.sdk.ZoomSDKInitializeListener;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;

/**
 * ZoomPlugin
 */
public class ZoomPlugin implements FlutterPlugin, MethodCallHandler, ActivityAware, ZoomSDKAuthenticationListener {
    /// The MethodChannel that will the communication between Flutter and native Android
    ///
    /// This local reference serves to register the plugin with the Flutter Engine and unregister it
    /// when the Flutter Engine is detached from the Activity
    private MethodChannel channel;
    private EventChannel meetingStatusChannel;
    private Context context;
    private EventChannel inMeetingEventChannel;


    public static Boolean disableScreenshotAndRecording = false;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        BinaryMessenger msg = flutterPluginBinding.getBinaryMessenger();
        context = flutterPluginBinding.getApplicationContext();
        channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "plugins.webcare/zoom_channel");
        channel.setMethodCallHandler(this);

        meetingStatusChannel = new EventChannel(msg, "plugins.webcare/zoom_event_stream");
        inMeetingEventChannel = new EventChannel(msg, "plugins.webcare/zoom_in_meeting_event_stream");
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        switch (call.method) {
            case "init":
                init(call, result);
                break;
            case "join":
                joinMeeting(call, result);
                break;
            case "start":
                startMeeting(call, result);
                break;
            case "meeting_status":
                meetingStatus(result);
                break;
            default:
                result.notImplemented();
        }
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
    }

    private void init(final MethodCall methodCall, final MethodChannel.Result result) {

        Map<String, String> options = methodCall.arguments();

        disableScreenshotAndRecording = parseBoolean(options, "disableScreenshotAndRecording", false);

        ZoomSDK zoomSDK = ZoomSDK.getInstance();

        if (zoomSDK.isInitialized()) {
            List<Integer> response = Arrays.asList(0, 0);
            result.success(response);
            return;
        }

        ZoomSDKInitParams initParams = new ZoomSDKInitParams();
        initParams.domain = options.get("domain");
        if (options.containsKey("jwtToken")) {
            initParams.jwtToken = options.get("jwtToken");
        }
        if (options.containsKey("appKey")) {
            initParams.appKey = options.get("appKey");
        }
        if (options.containsKey("appSecret")) {
            initParams.appSecret = options.get("appSecret");
        }
        zoomSDK.initialize(
                context,
                new ZoomSDKInitializeListener() {

                    @Override
                    public void onZoomAuthIdentityExpired() {

                    }

                    @Override
                    public void onZoomSDKInitializeResult(int errorCode, int internalErrorCode) {
                        List<Integer> response = Arrays.asList(errorCode, internalErrorCode);

                        if (errorCode != ZoomError.ZOOM_ERROR_SUCCESS) {
                            System.out.println("Failed to initialize Zoom SDK");
                            result.success(response);
                            return;
                        }

                        ZoomSDK zoomSDK = ZoomSDK.getInstance();
                        MeetingService meetingService = zoomSDK.getMeetingService();
                        meetingStatusChannel.setStreamHandler(new StatusStreamHandler(meetingService));

                        InMeetingService inMeetingService = zoomSDK.getInMeetingService();
                        inMeetingEventChannel.setStreamHandler(new InMeetingListener(inMeetingService));

                        zoomSDK.getZoomUIService().setNewMeetingUI(MyMeetingActivity.class);

                        result.success(response);
                    }
                },
                initParams);
    }

    private void joinMeeting(MethodCall methodCall, MethodChannel.Result result) {
        Map<String, String> options = methodCall.arguments();

        ZoomSDK zoomSDK = ZoomSDK.getInstance();

        if (!zoomSDK.isInitialized()) {
            result.success(false);
            return;
        }

        final MeetingService meetingService = zoomSDK.getMeetingService();

        JoinMeetingOptions opts = new JoinMeetingOptions();
        opts.no_invite = parseBoolean(options, "disableInvite", false);
        opts.no_share = parseBoolean(options, "disableShare", false);
        opts.no_driving_mode = parseBoolean(options, "disableDrive", false);
        opts.no_dial_in_via_phone = parseBoolean(options, "disableDialIn", false);
        opts.no_disconnect_audio = parseBoolean(options, "noDisconnectAudio", false);
        opts.no_audio = parseBoolean(options, "noAudio", false);
        opts.meeting_views_options = parseInt(options, "meetingViewOptions", 0);
        opts.invite_options = parseInt(options, "inviteOptions", 0);
        opts.no_meeting_end_message = parseBoolean(options, "noMeetingEndMessage", false);
        opts.no_meeting_error_message = parseBoolean(options, "noMeetingErrorMessage", false);
        opts.no_titlebar = parseBoolean(options, "noTitlebar", false);
        opts.no_bottom_toolbar = parseBoolean(options, "noBottomToolbar", false);
        opts.no_video = parseBoolean(options, "noVideo", false);

        boolean hideMeetingInviteUrl = parseBoolean(options, "disableInviteUrl", false);
        zoomSDK.getZoomUIService().hideMeetingInviteUrl(hideMeetingInviteUrl);

        JoinMeetingParams params = new JoinMeetingParams();
        params.displayName = options.get("displayName");
        params.meetingNo = options.get("meetingId");
        params.password = options.get("meetingPassword");

        int r = meetingService.joinMeetingWithParams(context, params, opts);
        result.success(r == MeetingError.MEETING_ERROR_SUCCESS);
    }

    private void startMeeting(MethodCall methodCall, MethodChannel.Result result) {
        Map<String, String> options = methodCall.arguments();

        ZoomSDK zoomSDK = ZoomSDK.getInstance();

        if (!zoomSDK.isInitialized()) {
            result.success(false);
            return;
        }

        final MeetingService meetingService = zoomSDK.getMeetingService();

        StartMeetingOptions opts = new StartMeetingOptions();
        opts.no_invite = parseBoolean(options, "disableInvite", false);
        opts.no_share = parseBoolean(options, "disableShare", false);
        opts.no_driving_mode = parseBoolean(options, "disableDrive", false);
        opts.no_dial_in_via_phone = parseBoolean(options, "disableDialIn", false);
        opts.no_disconnect_audio = parseBoolean(options, "noDisconnectAudio", false);
        opts.no_audio = parseBoolean(options, "noAudio", false);
        opts.meeting_views_options = parseInt(options, "meetingViewOptions", 0);
        opts.invite_options = parseInt(options, "inviteOptions", 0);
        opts.no_meeting_end_message = parseBoolean(options, "noMeetingEndMessage", false);
        opts.no_meeting_error_message = parseBoolean(options, "noMeetingErrorMessage", false);
        opts.no_titlebar = parseBoolean(options, "noTitlebar", false);
        opts.no_bottom_toolbar = parseBoolean(options, "noBottomToolbar", false);
        opts.no_video = parseBoolean(options, "noVideo", false);
        zoomSDK.getZoomUIService().hideMeetingInviteUrl(parseBoolean(options, "disableInviteUrl", false));

        StartMeetingParamsWithoutLogin params = new StartMeetingParamsWithoutLogin();

        params.displayName = options.get("displayName");
        params.meetingNo = options.get("meetingId");
        params.userType = MeetingService.USER_TYPE_API_USER;
        params.zoomAccessToken = options.get("zoomAccessToken");

        int r = meetingService.startMeetingWithParams(context, params, opts);
        result.success(r == MeetingError.MEETING_ERROR_SUCCESS);
    }

    private boolean parseBoolean(Map<String, String> options, String property, boolean defaultValue) {
        return options.get(property) == null ? defaultValue : Boolean.parseBoolean(options.get(property));
    }

    private int parseInt(Map<String, String> options, String property, int defaultValue) {
        return options.get(property) == null ? defaultValue : Integer.parseInt(options.get(property));
    }


    private void meetingStatus(MethodChannel.Result result) {
        ZoomSDK zoomSDK = ZoomSDK.getInstance();

        if (!zoomSDK.isInitialized()) {
            result.success(Arrays.asList("MEETING_STATUS_UNKNOWN", "SDK not initialized"));
            return;
        }

        MeetingService meetingService = zoomSDK.getMeetingService();

        if (meetingService == null) {
            result.success(Arrays.asList("MEETING_STATUS_UNKNOWN", "No status available"));
            return;
        }

        MeetingStatus status = meetingService.getMeetingStatus();
        result.success(status != null ? Arrays.asList(status.name(), "") : Arrays.asList("MEETING_STATUS_UNKNOWN", "No status available"));
    }


    @Override
    public void onZoomAuthIdentityExpired() {

    }

    @Override
    public void onZoomSDKLoginResult(long result) {

    }

    @Override
    public void onZoomSDKLogoutResult(long result) {

    }

    @Override
    public void onZoomIdentityExpired() {

    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {

    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {

    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {

    }

    @Override
    public void onDetachedFromActivity() {

    }

    @Override
    public void onNotificationServiceStatus(SDKNotificationServiceStatus status) {

    }
}
