// Copyright © 2018 650 Industries. All rights reserved.

#import <EXPermissions/EXUserNotificationRequester.h>

#import <UIKit/UIKit.h>
#import <UserNotifications/UserNotifications.h>

@interface EXUserNotificationRequester ()

@property (nonatomic, strong) EXPromiseResolveBlock resolve;
@property (nonatomic, strong) EXPromiseRejectBlock reject;
@property (nonatomic, weak) id<EXPermissionRequesterDelegate> delegate;

@end

@implementation EXUserNotificationRequester

+ (NSDictionary *)permissions
{
  dispatch_semaphore_t sem = dispatch_semaphore_create(0);
  __block BOOL allowsSound;
  __block BOOL allowsAlert;
  __block BOOL allowsBadge;
  __block EXPermissionStatus status;

  [[UNUserNotificationCenter currentNotificationCenter] getNotificationSettingsWithCompletionHandler:^(UNNotificationSettings *settings) {
    allowsSound = settings.soundSetting;
    allowsAlert = settings.alertStyle;
    allowsBadge = settings.badgeSetting;

    status = EXPermissionStatusUndetermined;

    if (settings.authorizationStatus == UNAuthorizationStatusAuthorized) {
      status = EXPermissionStatusGranted;
    } else {
      status = EXPermissionStatusDenied;
    }
    dispatch_semaphore_signal(sem);
  }];

  dispatch_semaphore_wait(sem, DISPATCH_TIME_FOREVER);

  return @{
           @"status": [EXPermissions permissionStringForStatus:status],
           @"allowsSound": @(allowsSound),
           @"allowsAlert": @(allowsAlert),
           @"allowsBadge": @(allowsBadge),
           @"expires": EXPermissionExpiresNever,
           };
}

- (void)setDelegate:(id<EXPermissionRequesterDelegate>)delegate
{
  _delegate = delegate;
}

- (void)requestPermissionsWithResolver:(EXPromiseResolveBlock)resolve rejecter:(EXPromiseRejectBlock)reject
{
  if (_resolve != nil || _reject != nil) {
    reject(@"E_AWAIT_PROMISE", @"Another request for the same permission is already being handled.", nil);
    return;
  }

  _resolve = resolve;
  _reject = reject;

  __weak EXUserNotificationRequester *weakSelf = self;

  UNAuthorizationOptions options = UNAuthorizationOptionAlert + UNAuthorizationOptionSound;
  [[UNUserNotificationCenter currentNotificationCenter] requestAuthorizationWithOptions:options
                                                           completionHandler:^(BOOL granted, NSError * _Nullable error) {
                                                             dispatch_async(dispatch_get_main_queue(), ^{
                                                               [weakSelf _consumeResolverWithCurrentPermissions];
                                                             });
  }];
}


- (void)_consumeResolverWithCurrentPermissions
{
  if (_resolve) {
    _resolve([[self class] permissions]);
    _resolve = nil;
    _reject = nil;
  }
  if (_delegate) {
    [_delegate permissionRequesterDidFinish:self];
  }
}

@end
