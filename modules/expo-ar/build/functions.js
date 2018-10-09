import { Dimensions, Platform, findNodeHandle, } from 'react-native';
import { Constants } from 'expo-constants';
import { EventEmitter, NativeModulesProxy } from 'expo-core';
import { EventType, TrackingConfiguration, } from './enums';
const ExpoAR = NativeModulesProxy.ExpoAR;
const AREventEmitter = new EventEmitter(ExpoAR);
export function isAvailable() {
    // if (
    //   !Constants.isDevice || // Prevent Simulators
    //   Platform.isTVOS ||
    //   (Platform.OS === 'ios' && Constants.deviceYearClass < 2015) || // iOS device has A9 chip
    //   // !ExpoAR.isSupported || // ARKit is included in the build
    //   !ExpoAR.startAsync // Older SDK versions (27 and lower) that are fully compatible
    // ) {
    //   console.log('AR.isAvailable: false');
    //   return false;
    // }
    return true;
}
const AvailabilityErrorMessages = {
    Simulator: `Cannot run EXGL in a simulator`,
    ANineChip: `ARKit can only run on iOS devices with A9 (2015) or greater chips! This is a`,
    ARKitOnlyOnIOS: `ARKit can only run on an iOS device! This is a`,
};
export function getUnavailabilityReason() {
    if (!Constants.isDevice) {
        return AvailabilityErrorMessages.Simulator;
    }
    else if (Platform.OS !== 'ios') {
        return `${AvailabilityErrorMessages.ARKitOnlyOnIOS} ${Platform.OS} device`;
    }
    else if (Constants.deviceYearClass < 2015) {
        return `${AvailabilityErrorMessages.ANineChip} ${Constants.deviceYearClass} device`;
    }
    return 'Unknown Reason';
}
export function onFrameDidUpdate(listener) {
    return _addListener(EventType.FrameDidUpdate, listener);
}
export function onDidFailWithError(listener) {
    return _addListener(EventType.DidFailWithError, listener);
}
export function onAnchorsDidUpdate(listener) {
    return _addListener(EventType.AnchorsDidUpdate, listener);
}
export function onCameraDidChangeTrackingState(listener) {
    return _addListener(EventType.CameraDidChangeTrackingState, listener);
}
export function onSessionWasInterrupted(listener) {
    return _addListener(EventType.SessionWasInterrupted, listener);
}
export function onSessionInterruptionEnded(listener) {
    return _addListener(EventType.SessionInterruptionEnded, listener);
}
function _addListener(eventType, event) {
    return AREventEmitter.addListener(eventType, event);
}
export function removeAllListeners(eventType) {
    AREventEmitter.removeAllListeners(eventType);
}
// TODO: support multiple types (take an array or bit flags)
export function performHitTest(point, types) {
    return ExpoAR.performHitTest(point, types);
}
export async function setDetectionImagesAsync(images) {
    return ExpoAR.setDetectionImagesAsync(images);
}
export async function getCurrentFrameAsync(attributes) {
    return ExpoAR.getCurrentFrameAsync(attributes);
}
export async function getMatricesAsync(near, far) {
    return ExpoAR.getMatricesAsync(near, far);
}
export async function stopAsync() {
    return ExpoAR.stopAsync();
}
export async function startAsync(node, configuration) {
    if (typeof node === 'number') {
        return await ExpoAR.startAsync(node, configuration);
    }
    else {
        const handle = findNodeHandle(node);
        if (handle === null) {
            throw new Error(`Could not find the React node handle for the AR component: ${node}`);
        }
        return await ExpoAR.startAsync(handle, configuration);
    }
}
export function reset() {
    ExpoAR.reset();
}
export function resume() {
    ExpoAR.resume();
}
export function pause() {
    ExpoAR.pause();
}
export async function setConfigurationAsync(configuration) {
    await ExpoAR.setConfigurationAsync(configuration);
}
export function getProvidesAudioData() {
    return ExpoAR.getProvidesAudioData();
}
export function setProvidesAudioData(providesAudioData) {
    ExpoAR.setProvidesAudioData(providesAudioData);
}
export async function setPlaneDetectionAsync(planeDetection) {
    return ExpoAR.setPlaneDetectionAsync(planeDetection);
}
export function getPlaneDetection() {
    return ExpoAR.getPlaneDetection();
}
export async function getCameraTextureAsync() {
    return ExpoAR.getCameraTextureAsync();
}
export async function setWorldOriginAsync(matrix_float4x4) {
    await ExpoAR.setWorldOriginAsync(matrix_float4x4);
}
export function setLightEstimationEnabled(isLightEstimationEnabled) {
    ExpoAR.setLightEstimationEnabled(isLightEstimationEnabled);
}
export function getLightEstimationEnabled() {
    return ExpoAR.getLightEstimationEnabled();
}
export function setAutoFocusEnabled(isAutoFocusEnabled) {
    ExpoAR.setAutoFocusEnabled(isAutoFocusEnabled);
}
export function getAutoFocusEnabled() {
    return ExpoAR.getAutoFocusEnabled();
}
export function setWorldAlignment(worldAlignment) {
    ExpoAR.setWorldAlignment(worldAlignment);
}
export function getWorldAlignment() {
    return ExpoAR.getWorldAlignment();
}
export function isConfigurationAvailable(configuration) {
    const { width, height } = Dimensions.get('window');
    // @ts-ignore: re-evaluate this for the new iPhones (2018)
    const isX = (width === 812 || height === 812) && !Platform.isTVOS && !Platform.isPad;
    if (configuration === TrackingConfiguration.Face && isX && isAvailable()) {
        return true;
    }
    return !!ExpoAR[configuration];
}
export function getSupportedVideoFormats(configuration) {
    const videoFormats = {
        [TrackingConfiguration.World]: 'WorldTrackingVideoFormats',
        [TrackingConfiguration.Orientation]: 'OrientationTrackingVideoFormats',
        [TrackingConfiguration.Face]: 'FaceTrackingVideoFormats',
    };
    const videoFormat = videoFormats[configuration];
    return ExpoAR[videoFormat] || [];
}
export function isFrontCameraAvailable() {
    return isConfigurationAvailable(TrackingConfiguration.Face);
}
export function isRearCameraAvailable() {
    return isConfigurationAvailable(TrackingConfiguration.World);
}
export function getVersion() {
    return ExpoAR.ARKitVersion;
}
//# sourceMappingURL=functions.js.map