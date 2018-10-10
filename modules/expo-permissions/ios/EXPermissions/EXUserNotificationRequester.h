// Copyright © 2018 650 Industries. All rights reserved.

#import <EXPermissions/EXPermissions.h>

@interface EXUserNotificationRequester : NSObject <EXPermissionRequester>

+ (NSDictionary *)permissions;

@end

