var exec = require('cordova/exec');

/**
 * 启动
 * @param { {} }              options 配置项:{timeFilter:int(时间过滤（秒）), distanceFilter:int(距离过滤（米）)}
 * @param { ()=>void }        success 成功回调
 * @param { message=>void }   error   失败回调
 */
function start(options, success, error) {
    exec(success, error, 'WiskindLBS', 'start', [options]);
}

/**
 * 停止
 * @param { ()=>void }        success 成功回调
 * @param { message=>void }   error   失败回调
 */
function stop(success, error) {
    exec(success, error, 'WiskindLBS', 'stop');
};

/**
 * 是否已经启动
 * @param { status=>void }    success 成功回调:status:{isStarted:boolean(是否已经启动，true表示已经启动)}
 * @param { message=>void }   error   失败回调
 */
function isStarted(success, error) {
    exec(success, error, 'WiskindLBS', 'isStarted');
};

/**
 * 定位
 * @param { location=>void }  success 成功回调:location:{lat:number(纬度), lng:number(经度)}
 * @param { message=>void }   error   失败回调
 */
function locate(success, error) {
    exec(success, error, 'WiskindLBS', 'locate');
};

/**
 * 静音
 * @param { boolean }         mute 是否静音
 * @param { location=>void }  success 成功回调:mute:{mute:boolean(是否静音)}
 * @param { message=>void }   error   失败回调
 */
function mute(mute, success, error) {
    exec(success, error, 'WiskindLBS', 'mute', [mute]);
};

/**
 * 是否静音
 * @param { location=>void }  success 成功回调:mute:{mute:boolean(是否静音)}
 * @param { message=>void }   error   失败回调
 */
function ismute(success, error) {
    exec(success, error, 'WiskindLBS', 'ismute');
};

exports.start = start;
exports.stop = stop;
exports.isStarted = isStarted;
exports.locate = locate;
exports.mute = mute;
exports.ismute = ismute;