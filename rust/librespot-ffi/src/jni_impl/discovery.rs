use jni::{
    JNIEnv,
    objects::{JClass, JObject},
    sys::jboolean,
};

use crate::{
    discovery::DiscoveryOutcome,
    jni_utils::{
        guard,
        jni_bridge::{BridgeArg, JavaCallback, dispatch},
    },
};

// DiscoveryCallback method indices. Must stay in sync with `DiscoveryCallback` in Kotlin.
const METHOD_LOGGED_IN: usize = 0;
const METHOD_FAILED: usize = 1;

const CALLBACK_METHODS: [(&str, &str); 2] = [
    ("onLoggedIn", "(Ljava/lang/String;)V"),
    ("onFailed", "(Ljava/lang/String;)V"),
];

#[unsafe(no_mangle)]
pub extern "system" fn Java_cc_tomko_outify_core_Discovery_start(
    mut env: JNIEnv,
    _this: JClass,
    callback: JObject,
) -> jboolean {
    guard("Discovery.start", 0, || {
        let callback = match JavaCallback::register(&mut env, callback, &CALLBACK_METHODS) {
            Ok(c) => c,
            Err(e) => {
                error!("jni register failed for discovery callback: {e}");
                return 0;
            }
        };

        let started = crate::discovery::start(move |outcome| {
            let (idx, arg) = match outcome {
                DiscoveryOutcome::LoggedIn { username } => (METHOD_LOGGED_IN, username),
                DiscoveryOutcome::Failed(reason) => (METHOD_FAILED, reason),
            };
            dispatch(callback.clone(), idx, vec![BridgeArg::Str(arg)]);
        });

        started.is_ok() as jboolean
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_cc_tomko_outify_core_Discovery_stop(_env: JNIEnv, _this: JClass) {
    guard("Discovery.stop", (), crate::discovery::stop)
}
