#import <Cordova/CDV.h>
#import <BMKLocationKit/BMKLocationAuth.h>
#import <BMKLocationKit/BMKLocationComponent.h>
#import <CoreLocation/CLLocationManager.h>
#import <AVFoundation/AVFoundation.h>
#import "WiskindLBSDBHelper.h"

/**
 * 启停切换结果
 */
typedef NS_ENUM(NSInteger, WLBSToggleResult)
{
    WLBSToggleUnknown,        // 未知
    WLBSToggleSuccess,        // 成功
    WLBSToggleUnsaved,        // 无法保存状态，序列化用户默认设置失败
    WLBSToggleDisabled,       // 定位服务未打开
    WLBSToggleUnauthed,       // 定位服务不允许访问
};

/**
 * 检查目的
 */
typedef NS_ENUM(NSInteger, WLBSCheckFor)
{
    WLBSCheckForNothing,      // 无
    WLBSCheckForInit,         // 初始化
    WLBSCheckForStart,        // 启动
    WLBSCheckForLocate,       // 定位
};

/**
 * 声音类型
 */
typedef NS_ENUM(NSInteger, WLBSAudioType)
{
    WLBSAudioDataError,       // 定位数据错误
    WLBSAudioDBError,         // 定位存储错误
    WLBSAudioServiceError,    // 定位服务错误
    WLBSAudioServiceDisabled, // 定位服务未打开
    WLBSAudioServiceUnauthed, // 定位服务不允许访问
};

@interface WiskindLBS : CDVPlugin <BMKLocationAuthDelegate, BMKLocationManagerDelegate, AVAudioPlayerDelegate>

/**
 * 启动
 *
 * @param command 命令
 */
- (void)start:(CDVInvokedUrlCommand*)command;

/**
 * 停止
 *
 * @param command 命令
 */
- (void)stop:(CDVInvokedUrlCommand*)command;

/**
 * 是否已经启动
 *
 * @param command 命令
 */
- (void)isStarted:(CDVInvokedUrlCommand*)command;

/**
 * 定位，获取当前位置
 *
 * @param command 命令
 */
- (void)locate:(CDVInvokedUrlCommand*)command;

/**
 * 静音
 *
 * @param command 命令
 */
- (void)mute:(CDVInvokedUrlCommand*)command;

/**
 * 是否静音
 *
 * @param command 命令
 */
- (void)ismute:(CDVInvokedUrlCommand*)command;

@end

@interface WiskindLBS()

#pragma mark - 系统

/** 应用名称 */
@property (nonatomic, copy) NSString *appName;

/** 用户默认设置 */
@property (nonatomic, retain) NSUserDefaults *userDefaults;

#pragma mark - 鉴权

/** 百度AK */
@property (nonatomic, copy) NSString *ak;

/** 定位鉴权错误码 */
@property (nonatomic) BMKLocationAuthErrorCode authErrorCode;

/** 鉴权目的 */
@property (nonatomic) WLBSCheckFor checkFor;

#pragma mark - 参数

/** 时间过滤 */
@property (nonatomic) NSInteger timeFilter;

/** 距离过滤 */
@property (nonatomic) NSInteger distanceFilter;

/** 是否已经启动 */
@property (nonatomic) BOOL started;

/** 最后一次存储的时间 */
@property (nonatomic) NSTimeInterval lastStoredTime;

#pragma mark - 定位管理器

/** 百度连续定位管理器 */
@property (nonatomic, retain) BMKLocationManager *continuousLocationManager;

/** 百度单次定位管理器 */
@property (nonatomic, retain) BMKLocationManager *singleLocationManager;

/** 权限申请定位管理器 */
@property (nonatomic, retain) CLLocationManager *authLocationManager;

#pragma mark - Cordova回调

/** 启动操作回调标识 */
@property (nonatomic, copy) NSString *startCallbackId;

/** 定位操作回调标识 */
@property (nonatomic, copy) NSString *locateCallbackId;

#pragma mark - 播放器

/** 定位数据错误播放器 */
@property (nonatomic) AVAudioPlayer *dataErrorPlayer;

/** 定位服务错误播放器 */
@property (nonatomic) AVAudioPlayer *serviceErrorPlayer;

/** 定位服务未打开播放器 */
@property (nonatomic) AVAudioPlayer *serviceDisabledPlayer;

/** 定位服务不允许访问播放器 */
@property (nonatomic) AVAudioPlayer *serviceUnauthPlayer;

/** 播放器集合 */
@property (nonatomic, retain) NSMutableDictionary<NSNumber *, AVAudioPlayer *> *audioPlayers;

/** 静默播放器 */
@property (nonatomic, retain) AVAudioPlayer *silencePlayer;

#pragma mark - 数据库

/** 数据库工具 */
@property (nonatomic, retain) WiskindLBSDBHelper *dbHelper;

#pragma mark - 定位服务

/** 监视定位服务定时器 */
@property (nonatomic, retain) NSTimer *watchTimer;

/** 运行日志定时器 */
@property (nonatomic, retain) NSTimer *runlogTimer;

@end

@implementation WiskindLBS

/** 时间过滤键名:int（秒） */
NSString *const kTimeFilter = @"timeFilter";

/** 距离过滤键名:int（米） */
NSString *const kDistanceFilter = @"distanceFilter";

/** 正在定位键名:boolean */
NSString *const kWLBSLocating = @"wlbs_locating";

/** 定位服务时间过滤键名:int（秒） */
NSString *const kWLBSTimeFilter = @"wlbs_time_filter";

/** 定位服务距离过滤键名:int（米） */
NSString *const kWLBSDistanceFilter = @"wlbs_distance_filter";

/** 定位服务静音模式（不进行语音提醒）:boolean */
NSString *const kWLBSMute = @"wlbs_mute";

/** 百度定位错误返回码含义对照 */
NSDictionary<NSNumber *, NSString *> *const kBLError =
                                    @{
                                      @(BMKLocationErrorUnknown):                 @"未知错误",
                                      @(BMKLocationErrorLocateFailed):            @"定位错误",
                                      @(BMKLocationErrorReGeocodeFailed):         @"逆地理错误",
                                      @(BMKLocationErrorTimeOut):                 @"超时",
                                      @(BMKLocationErrorCanceled):                @"取消",
                                      @(BMKLocationErrorCannotFindHost):          @"找不到主机",
                                      @(BMKLocationErrorBadURL):                  @"URL异常",
                                      @(BMKLocationErrorNotConnectedToInternet):  @"连接异常",
                                      @(BMKLocationErrorCannotConnectToHost):     @"服务器连接失败",
                                      @(BMKLocationErrorHeadingFailed):           @"获取方向失败",
                                      @(BMKLocationErrorFailureAuth):             @"鉴权失败",
                                      };

/** 定位鉴权错误码含义对照 */
NSDictionary<NSNumber *, NSString *> *const kBLAuthError =
                                    @{
                                      @(BMKLocationAuthErrorUnknown):             @"未知错误",
                                      @(BMKLocationAuthErrorSuccess):             @"鉴权成功",
                                      @(BMKLocationAuthErrorNetworkFailed):       @"因网络鉴权失败",
                                      @(BMKLocationAuthErrorFailed):              @"KEY非法鉴权失败",
                                      };

/** 声音文件名称 */
NSDictionary<NSNumber *, NSString *> *const kAudioFileName =
                                    @{
                                      @(WLBSAudioDataError):                      @"loc_data_error",
                                      @(WLBSAudioDBError):                        @"loc_db_error",
                                      @(WLBSAudioServiceError):                   @"loc_service_error",
                                      @(WLBSAudioServiceDisabled):                @"loc_service_disabled",
                                      @(WLBSAudioServiceUnauthed):                @"loc_service_unauthed",
                                      };

#pragma mark - 生命周期

/**
 * 插件初始化
 */
- (void)pluginInitialize {

    // 设置声音后台播放模式
    AVAudioSession *session = [AVAudioSession sharedInstance];
    [session setCategory:AVAudioSessionCategoryPlayback error:nil];
    [session setActive:YES error:nil];
    // 初始化播放器集合
    self.audioPlayers = [NSMutableDictionary dictionary];
    // 获取信息字典
    NSDictionary *info = [[NSBundle mainBundle] infoDictionary];
    // 获取百度AK
    self.ak = [[info valueForKey:@"BaiduLoc"] valueForKey:@"AK"];
    // 获取应用名称
    self.appName = [info valueForKey:@"CFBundleDisplayName"];
    // 初始化定位鉴权错误码
    self.authErrorCode = BMKLocationAuthErrorUnknown;
    // 获取用户默认设置
    self.userDefaults = [NSUserDefaults standardUserDefaults];

    // 初始化数据库工具
    if (!self.dbHelper) {
        self.dbHelper = [[WiskindLBSDBHelper alloc] init];
        BOOL success = [self.dbHelper open];
        if (!success) {
            [self playAudio:WLBSAudioDBError];
            self.dbHelper = nil;
        }
    }
    // 删除30天前的运行日志
    if (self.dbHelper) {
        [self.dbHelper deleteRunlog];
    }
    // 启动运行日志定时器
    if (!self.runlogTimer) {
        self.runlogTimer = [NSTimer scheduledTimerWithTimeInterval:60 target:self selector:@selector(runlogTimerHandle) userInfo:nil repeats:YES];
    }

    // 获取正在定位状态
    BOOL locating = [self.userDefaults boolForKey:kWLBSLocating];

    if (locating) {
        NSInteger timeFilter = [self.userDefaults integerForKey:kWLBSTimeFilter];
        NSInteger distanceFilter = [self.userDefaults integerForKey:kWLBSDistanceFilter];

        if (timeFilter > 0 && distanceFilter > 0) {
            self.timeFilter = timeFilter;
            self.distanceFilter = distanceFilter;
            if ([self authSuccess]) {
                if (self.continuousLocationManager) {
                    self.continuousLocationManager.distanceFilter = self.distanceFilter;
                } else {
                    [self initContinuousLocationManager];
                }
                if ([self disabled] || [self unauthed]) {
                    [self alert:@"无法获取定位！\r\n请打开“定位服务”\r\n并允许访问"];
                }
                [self begin];
            } else {
                [self checkPermision:WLBSCheckForInit];
            }
        } else {
            [self.userDefaults removeObjectForKey:kWLBSLocating];
            [self.userDefaults removeObjectForKey:kWLBSTimeFilter];
            [self.userDefaults removeObjectForKey:kWLBSDistanceFilter];
            [self.userDefaults synchronize];
        }
    }
}

- (void)dispose {
    [super dispose];
    [self.dbHelper close];
    [self.silencePlayer stop];
    [self.watchTimer invalidate];
    self.watchTimer = nil;
    [self.runlogTimer invalidate];
    self.runlogTimer = nil;
}

#pragma mark - 桥接方法

- (void)start:(CDVInvokedUrlCommand*)command
{
    NSDictionary *dic = [command.arguments objectAtIndex:0];
    self.timeFilter = [[dic valueForKey:kTimeFilter] integerValue];
    self.distanceFilter = [[dic valueForKey:kDistanceFilter] integerValue];

    if ([self authSuccess]) {
        if (self.continuousLocationManager) {
            self.continuousLocationManager.distanceFilter = self.distanceFilter;
        } else {
            [self initContinuousLocationManager];
        }
        [self callbackStart:command.callbackId];
    } else {
        [self checkPermisionForStart:command.callbackId];
    }
}

- (void)stop:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult *pr;
    if ([self authSuccess]) {
        if (self.continuousLocationManager) {
            WLBSToggleResult tr = [self toggleStop];
            switch (tr) {
                case WLBSToggleSuccess:
                    pr = [self buildSuccess];
                    break;
                    
                case WLBSToggleUnsaved:
                    pr = [self buildError:@"保存停止状态失败"];
                    break;

                default:
                    pr = [self buildError:@"定位服务停止未知异常"];
                    break;
            }
        } else {
            pr = [self buildError:@"定位服务未初始化"];
        }
    } else {
        pr = [self buildError:@"定位服务未初始化"];
    }
    [self.commandDelegate sendPluginResult:pr callbackId:command.callbackId];
}

- (void)isStarted:(CDVInvokedUrlCommand *)command
{
    [self sendSuccess:@{@"isStarted":@(self.started)} callbackId:command.callbackId];
}

- (void)locate:(CDVInvokedUrlCommand*)command
{
    if ([self authSuccess]) {
        if (!self.singleLocationManager) {
            [self initSingleLocationManager];
        }
        [self callbackLocate:command.callbackId];
    } else {
        [self checkPermisionForLocate:command.callbackId];
    }
}

- (void)mute:(CDVInvokedUrlCommand*)command
{
    if (![command.arguments count]) {
        [self sendError:@"缺少参数" callbackId:command.callbackId];
    }
    NSNumber *mute = [command.arguments objectAtIndex:0];
    [self.userDefaults setBool:mute.integerValue forKey:kWLBSMute];

    if ([self.userDefaults synchronize]) {
        [self sendSuccess:@{@"mute":mute} callbackId:command.callbackId];
    } else {
        [self sendError:@"静音模式切换失败" callbackId:command.callbackId];
    }
}

- (void)ismute:(CDVInvokedUrlCommand*)command
{
    BOOL mute = [self.userDefaults boolForKey:kWLBSMute];
    [self sendSuccess:@{@"mute":@(mute)} callbackId:command.callbackId];
}

#pragma mark - 其他

/**
 * 回调启动
 *
 * @param callbackId 回调标识
 */

- (void)callbackStart:(NSString *)callbackId
{
    CDVPluginResult *pr;
    WLBSToggleResult tr = [self toggleStart];
    switch (tr) {
        case WLBSToggleSuccess:
            pr = [self buildSuccess];
            break;

        case WLBSToggleUnsaved:
            pr = [self buildError:@"保存启动状态失败"];
            break;

        case WLBSToggleDisabled:
            pr = [self buildError:@"定位服务未打开"];
            break;

        case WLBSToggleUnauthed:
            pr = [self buildError:@"定位服务不允许访问"];
            break;

        case WLBSToggleUnknown:
            pr = [self buildError:@"定位服务启动未知异常"];
            break;
    }
    [self.commandDelegate sendPluginResult:pr callbackId:callbackId];
}

/**
 * 回调定位
 *
 * @param callbackId 回调标识
 */

- (void)callbackLocate:(NSString *)callbackId
{
    BOOL success = [self.singleLocationManager requestLocationWithReGeocode:NO withNetworkState:NO completionBlock:^(BMKLocation * _Nullable location, BMKLocationNetworkState state, NSError * _Nullable error) {
        if (error || !location) {
            if ([self disabled]) {
                return [self sendError:@"定位服务未打开" callbackId:callbackId];
            }
            if ([self unauthed]) {
                return [self sendError:@"定位服务不允许访问" callbackId:callbackId];
            }
            NSString *message;
            if (error) {
                message = [kBLError objectForKey:@(error.code)];
            }
            if (!message) {
                message = @"未知错误";
            }
            [self sendError:[NSString stringWithFormat:@"无法定位：%@", message] callbackId:callbackId];
        } else {
            CLLocationDegrees latitude = location.location.coordinate.latitude;
            CLLocationDegrees longitude = location.location.coordinate.longitude;
            NSDictionary *dictionary = @{@"lat":@(latitude), @"lng":@(longitude)};
            [self sendSuccess:dictionary callbackId:callbackId];
        }
    }];
    if (!success) {
        [self sendError:@"定位请求失败" callbackId:callbackId];
    }
}

/**
 * 切换为启动
 *
 * @return 启停切换结果
 */
- (WLBSToggleResult)toggleStart
{
    if ([self disabled]) {
        self.authLocationManager = [[CLLocationManager alloc] init];
        [self.authLocationManager requestAlwaysAuthorization];
        return WLBSToggleDisabled;
    }
    if ([self unauthed]) {
        self.authLocationManager = [[CLLocationManager alloc] init];
        [self.authLocationManager requestAlwaysAuthorization];
        return WLBSToggleUnauthed;
    }
    self.continuousLocationManager.distanceFilter = self.distanceFilter;
    [self begin];

    [self.userDefaults setBool:YES forKey:kWLBSLocating];
    [self.userDefaults setInteger:self.timeFilter forKey:kWLBSTimeFilter];
    [self.userDefaults setInteger:self.distanceFilter forKey:kWLBSDistanceFilter];

    if ([self.userDefaults synchronize]) {
        return WLBSToggleSuccess;
    } else {
        [self end];
        return WLBSToggleUnsaved;
    }
}

/**
 * 切换为停止
 *
 * @return 启停切换结果
 */
- (WLBSToggleResult)toggleStop
{
    [self end];

    [self.userDefaults removeObjectForKey:kWLBSLocating];
    [self.userDefaults removeObjectForKey:kWLBSTimeFilter];
    [self.userDefaults removeObjectForKey:kWLBSDistanceFilter];

    if ([self.userDefaults synchronize]) {
        return WLBSToggleSuccess;
    } else {
        [self begin];
        return WLBSToggleUnsaved;
    }
}

/**
 * 开始
 */
- (void)begin
{
    if (!self.watchTimer) {
        self.watchTimer = [NSTimer scheduledTimerWithTimeInterval:self.timeFilter target:self selector:@selector(watch) userInfo:nil repeats:YES];
    }
    [self playSilenceAudio];
    [self.continuousLocationManager startUpdatingLocation];
    self.started = YES;
}

/**
 * 结束
 */
- (void)end
{
    [self.continuousLocationManager stopUpdatingLocation];
    self.started = NO;
    [self.silencePlayer stop];
    [self.watchTimer invalidate];
    self.watchTimer = nil;
}

/**
 * 监视
 */
- (void)watch
{
    if ([self disabled]) {
        [self playAudio:WLBSAudioServiceDisabled];
    } else if ([self unauthed]) {
        [self playAudio:WLBSAudioServiceUnauthed];
    }
}

/**
 * 运行日志定时器处理
 */
- (void)runlogTimerHandle
{
    if (!self.dbHelper) {
        self.dbHelper = [[WiskindLBSDBHelper alloc] init];
        BOOL success = [self.dbHelper open];
        if (!success) {
            [self playAudio:WLBSAudioDBError];
            self.dbHelper = nil;
            return;
        }
    }
    [self.dbHelper saveRunlog];
}

#pragma mark - 初始化

/**
 * 初始化设置百度连续定位管理器
 */
- (void)initContinuousLocationManager
{
    // 初始化实例
    self.continuousLocationManager = [[BMKLocationManager alloc] init];
    // 设置delegate
    self.continuousLocationManager.delegate = self;
    // 设置返回位置的坐标系类型
    self.continuousLocationManager.coordinateType = BMKLocationCoordinateTypeBMK09LL;
    // 设置距离过滤参数
    self.continuousLocationManager.distanceFilter = self.distanceFilter;
    // 设置预期精度参数
    // self.continuousLocationManager.desiredAccuracy = kCLLocationAccuracyBest;
    // 设置应用位置类型
    // self.continuousLocationManager.activityType = CLActivityTypeAutomotiveNavigation;
    // 设置是否自动停止位置更新
    // self.continuousLocationManager.pausesLocationUpdatesAutomatically = NO;
    // 设置是否允许后台定位
    self.continuousLocationManager.allowsBackgroundLocationUpdates = YES;
    // 设置位置获取超时时间
    // self.continuousLocationManager.locationTimeout = 10;
    // 设置获取地址信息超时时间
    // self.continuousLocationManager.reGeocodeTimeout = 10;
    // 连续定位是否返回逆地理信息
    self.continuousLocationManager.locatingWithReGeocode = NO;
}

/**
 * 初始化设置百度单次定位管理器
 */
- (void)initSingleLocationManager
{
    // 初始化实例
    self.singleLocationManager = [[BMKLocationManager alloc] init];
    // 设置delegate
    // self.singleLocationManager.delegate = self;
    // 设置返回位置的坐标系类型
    self.singleLocationManager.coordinateType = BMKLocationCoordinateTypeBMK09LL;
    // 设置距离过滤参数
    // self.singleLocationManager.distanceFilter = self.distanceFilter;
    // 设置预期精度参数
    self.singleLocationManager.desiredAccuracy = kCLLocationAccuracyHundredMeters;
    // 设置应用位置类型
    // self.singleLocationManager.activityType = CLActivityTypeAutomotiveNavigation;
    // 设置是否自动停止位置更新
    // self.singleLocationManager.pausesLocationUpdatesAutomatically = NO;
    // 设置是否允许后台定位
    // self.singleLocationManager.allowsBackgroundLocationUpdates = YES;
    // 设置位置获取超时时间
    // self.singleLocationManager.locationTimeout = 10;
    // 设置获取地址信息超时时间
    // self.singleLocationManager.reGeocodeTimeout = 10;
    // 连续定位是否返回逆地理信息
    // self.singleLocationManager.locatingWithReGeocode = NO;
}

#pragma mark - 鉴权

/**
 * 检查百度定位权限。该方法会被多处调用，但是异步回调相同的代理方法，所以使用checkFor区分调用源头
 *
 * @param checkFor 检查目的
 */
- (void)checkPermision:(WLBSCheckFor)checkFor
{
    self.checkFor = checkFor;
    [[BMKLocationAuth sharedInstance] checkPermisionWithKey:self.ak authDelegate:self];
}

/**
 * 启动时检查百度定位权限
 *
 * @param callbackId 回调标识
 */
- (void)checkPermisionForStart:(NSString *)callbackId
{
    self.startCallbackId = callbackId;
    [self checkPermision:WLBSCheckForStart];
}

/**
 * 定位时检查百度定位权限
 *
 * @param callbackId 回调标识
 */
- (void)checkPermisionForLocate:(NSString *)callbackId
{
    self.locateCallbackId = callbackId;
    [self checkPermision:WLBSCheckForLocate];
}

/**
 * 是否百度定位鉴权成功
 *
 * @return 真，如果百度定位鉴权成功
 */
- (BOOL)authSuccess
{
    return self.authErrorCode == BMKLocationAuthErrorSuccess;
}

#pragma mark - 定位服务

/**
 * 是否定位服务未开启
 *
 * @return 真，如果定位服务未开启
 */
- (BOOL)disabled
{
    return ![CLLocationManager locationServicesEnabled];
}

/**
 * 是否定位服务不允许访问
 *
 * @return 真，如果定位服务不允许访问
 */
- (BOOL)unauthed
{
    CLAuthorizationStatus status = [CLLocationManager authorizationStatus];
    return status == kCLAuthorizationStatusNotDetermined
        || status == kCLAuthorizationStatusRestricted
        || status == kCLAuthorizationStatusDenied;
}

#pragma mark - Cordova消息

/**
 * 构建成功
 *
 * @return Cordova插件结果
 */
- (CDVPluginResult *)buildSuccess
{
    return [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
}

/**
 * 构建成功
 *
 * @param  dictionary 字典消息
 * @return Cordova插件结果
 */
- (CDVPluginResult *)buildSuccess:(NSDictionary *)dictionary
{
    return [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:dictionary];
}

/**
 * 构建错误
 *
 * @param  message 文本消息
 * @return Cordova插件结果
 */
- (CDVPluginResult *)buildError:(NSString *)message
{
    return [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:message];
}

/**
 * 发送成功回调
 *
 * @param dictionary 字典消息
 * @param callbackId 回调标识
 */
- (void)sendSuccess:(NSDictionary *)dictionary callbackId:(NSString *)callbackId
{
    [self.commandDelegate sendPluginResult:[self buildSuccess:dictionary] callbackId:callbackId];
}

/**
 * 发送错误回调
 *
 * @param message    文本消息
 * @param callbackId 回调标识
 */
- (void)sendError:(NSString *)message callbackId:(NSString *)callbackId
{
    [self.commandDelegate sendPluginResult:[self buildError:message] callbackId:callbackId];
}

#pragma mark - 系统

/**
 * 警告
 *
 * @param message 消息
 */
- (void)alert:(NSString *)message
{
    UIAlertAction *okAction = [UIAlertAction actionWithTitle:@"确定" style:UIAlertActionStyleDefault handler:nil];
    UIAlertController *alertController = [UIAlertController alertControllerWithTitle:message message:nil preferredStyle:UIAlertControllerStyleAlert];
    [alertController addAction:okAction];
    [self.viewController presentViewController:alertController animated:YES completion:nil];
}

#pragma mark - 声音 Audio

/**
 * 播放音乐
 *
 * @param 类型
 */
- (void)playAudio:(WLBSAudioType)type
{
    BOOL mute = [self.userDefaults boolForKey:kWLBSMute];
    if (mute) {
        return;
    }

    NSNumber *key = @(type);
    AVAudioPlayer *player = [self.audioPlayers objectForKey:key];
    if (!player) {
        NSString *name = [kAudioFileName objectForKey:key];
        NSURL *url = [[NSBundle mainBundle] URLForResource:name withExtension:@"mp3"];
        player = [[AVAudioPlayer alloc] initWithContentsOfURL:url error:nil];
        player.volume = 1;
        [self.audioPlayers setObject:player forKey:key];
    }
    NSEnumerator<NSNumber *> *enumerator = [self.audioPlayers keyEnumerator];
    BOOL playing = NO;
    while (key = [enumerator nextObject]) {
        if ([[self.audioPlayers objectForKey:key] isPlaying]) {
            playing = YES;
            break;
        }
    }
    if (!playing) {
        [player play];
    }
}

/**
 * 播放静默声音
 */
- (void)playSilenceAudio
{
    if (!self.silencePlayer) {
        NSURL *url = [[NSBundle mainBundle] URLForResource:@"loc_silence" withExtension:@"wav"];
        self.silencePlayer = [[AVAudioPlayer alloc] initWithContentsOfURL:url error:nil];
        self.silencePlayer.volume = 0;
        self.silencePlayer.numberOfLoops = -1;
        self.silencePlayer.delegate = self;
    }
    if (![self.silencePlayer isPlaying]) {
        [self.silencePlayer play];
    }
}

#pragma mark - 百度定位鉴权代理 BMKLocationAuthDelegate

/**
 * 返回授权验证错误
 *
 * @param iError 错误号 : 为0时验证通过，具体参加BMKLocationAuthErrorCode
 */
- (void)onCheckPermissionState:(BMKLocationAuthErrorCode)iError
{
    self.authErrorCode = iError;
    if (BMKLocationAuthErrorSuccess == iError) {
        switch (self.checkFor) {
            case WLBSCheckForInit:
                [self initContinuousLocationManager];
                if ([self disabled] || [self unauthed]) {
                    [self alert:@"无法获取定位！\r\n请打开“定位服务”\r\n并允许访问"];
                }
                [self begin];
                break;

            case WLBSCheckForStart:
                [self initContinuousLocationManager];
                [self callbackStart:self.startCallbackId];
                self.startCallbackId = nil;
                break;

            case WLBSCheckForLocate:
                [self initSingleLocationManager];
                [self callbackLocate:self.locateCallbackId];
                self.locateCallbackId = nil;
                break;

            default:
                break;
        }
        self.checkFor = WLBSCheckForNothing;
    } else {
        NSString *message = [kBLAuthError objectForKey:@(iError)];
        message = [NSString stringWithFormat:@"定位鉴权异常：%@", message];
        switch (self.checkFor) {
            case WLBSCheckForInit: {
                [self alert:message];
                break;
            }

            case WLBSCheckForStart: {
                [self sendError:message callbackId:self.startCallbackId];
                self.startCallbackId = nil;
                break;
            }

            case WLBSCheckForLocate:
                [self sendError:message callbackId:self.locateCallbackId];
                self.locateCallbackId = nil;
                break;

            default:
                break;
        }
        self.checkFor = WLBSCheckForNothing;
    }
}

#pragma mark - 百度定位管理器代理 BMKLocationManagerDelegate

/**
 * 当定位发生错误时，会调用代理的此方法
 *
 * @param manager 定位BMKLocationManager类
 * @param error   返回的错误，参考CLError
 */
- (void)BMKLocationManager:(BMKLocationManager * _Nonnull)manager didFailWithError:(NSError * _Nullable)error
{
    [self playAudio:WLBSAudioServiceError];
}

/**
 * 连续定位回调函数
 *
 * @param manager  定位BMKLocationManager类
 * @param location 定位结果，参考BMKLocation
 * @param error    错误信息
 */
- (void)BMKLocationManager:(BMKLocationManager * _Nonnull)manager didUpdateLocation:(BMKLocation * _Nullable)location orError:(NSError * _Nullable)error
{
    if (error || !location) {
        [self playAudio:WLBSAudioDataError];
    } else {
        CLLocationDegrees latitude = location.location.coordinate.latitude;
        CLLocationDegrees longitude = location.location.coordinate.longitude;
        NSTimeInterval ti = [[NSDate date] timeIntervalSince1970];
        BOOL validTime = ti - self.lastStoredTime >= self.timeFilter;
        if (validTime) {
            self.lastStoredTime = ti;
            if (!self.dbHelper) {
                self.dbHelper = [[WiskindLBSDBHelper alloc] init];
                BOOL success = [self.dbHelper open];
                if (!success) {
                    [self playAudio:WLBSAudioDBError];
                    self.dbHelper = nil;
                    return;
                }
            }
            NSString *lat = [NSString stringWithFormat:@"%f", latitude];
            NSString *lng = [NSString stringWithFormat:@"%f", longitude];
            NSInteger ts = ti * 1000;
            BOOL success = [self.dbHelper saveLat:lat lng:lng cdate:ts];
            if (!success) {
                [self playAudio:WLBSAudioDBError];
            }
        }
    }
}

@end
