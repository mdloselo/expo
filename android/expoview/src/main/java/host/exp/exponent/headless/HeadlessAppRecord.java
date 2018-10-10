package host.exp.exponent.headless;

import expo.loaders.provider.interfaces.AppRecordInterface;
import host.exp.exponent.RNObject;

public class HeadlessAppRecord implements AppRecordInterface {
  private RNObject mReactInstanceManager;

  public HeadlessAppRecord() {
    super();
  }

  public void setReactInstanceManager(RNObject reactInstanceManager) {
    mReactInstanceManager = reactInstanceManager;
  }

  public void invalidate() {
    if (mReactInstanceManager != null) {
      mReactInstanceManager.callRecursive("destroy");
      mReactInstanceManager = null;
    }
  }
}
