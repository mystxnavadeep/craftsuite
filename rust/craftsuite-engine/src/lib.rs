//! CraftSuite Native Engine JNI Bridge
//! Connects native Android Kotlin layer to upstream WordCraft, GridCraft, and DeckCraft engines.

use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::jstring;
use serde_json::{json, Value};
use std::sync::Mutex;

// Global real upstream headless session state for each editor type
static WORD_SESSION: Mutex<Option<wordcraft_mcp::Headless>> = Mutex::new(None);
static GRID_SESSION: Mutex<Option<gridcraft_mcp::headless::Headless>> = Mutex::new(None);
static DECK_SESSION: Mutex<Option<deckcraft_mcp::headless::Headless>> = Mutex::new(None);

fn safe_string(env: &mut JNIEnv, j_str: &JString) -> String {
    match env.get_string(j_str) {
        Ok(java_str) => java_str.into(),
        Err(_) => String::new(),
    }
}

fn create_jstring(env: &mut JNIEnv, rust_str: &str) -> jstring {
    match env.new_string(rust_str) {
        Ok(js) => js.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_storytold_craftsuite_core_enginebridge_NativeEngineBridge_nativeExecuteTool(
    mut env: JNIEnv,
    _class: JClass,
    engine_type: JString,
    tool_name: JString,
    json_args: JString,
) -> jstring {
    let engine = safe_string(&mut env, &engine_type);
    let tool = safe_string(&mut env, &tool_name);
    let args_raw = safe_string(&mut env, &json_args);

    let parsed_args: Value = serde_json::from_str(&args_raw).unwrap_or(json!({}));

    let result = match engine.as_str() {
        "word" => execute_word_tool(&tool, &parsed_args),
        "grid" => execute_grid_tool(&tool, &parsed_args),
        "deck" => execute_deck_tool(&tool, &parsed_args),
        _ => json!({"is_error": true, "content": [{"type": "text", "text": format!("Unknown engine type: {}", engine)}]}),
    };

    let result_str = serde_json::to_string(&result).unwrap_or_else(|_| "{}".to_string());
    create_jstring(&mut env, &result_str)
}

#[no_mangle]
pub extern "system" fn Java_com_storytold_craftsuite_core_enginebridge_NativeEngineBridge_nativeGetToolDefinitions(
    mut env: JNIEnv,
    _class: JClass,
    engine_type: JString,
) -> jstring {
    let engine = safe_string(&mut env, &engine_type);
    let tools = match engine.as_str() {
        "word" => wordcraft_mcp::tools::tool_definitions(),
        "grid" => json!(gridcraft_mcp::tools::tool_definitions()),
        "deck" => json!(deckcraft_mcp::tools::tool_definitions()),
        _ => json!([]),
    };
    let s = serde_json::to_string(&tools).unwrap_or_else(|_| "[]".to_string());
    create_jstring(&mut env, &s)
}

#[no_mangle]
pub extern "system" fn Java_com_storytold_craftsuite_core_enginebridge_NativeEngineBridge_nativeResetSession(
    mut env: JNIEnv,
    _class: JClass,
    engine_type: JString,
) -> jstring {
    let engine = safe_string(&mut env, &engine_type);
    match engine.as_str() {
        "word" => {
            if let Ok(mut lock) = WORD_SESSION.lock() {
                *lock = Some(wordcraft_mcp::Headless::default());
            }
        }
        "grid" => {
            if let Ok(mut lock) = GRID_SESSION.lock() {
                *lock = Some(gridcraft_mcp::headless::Headless::new());
            }
        }
        "deck" => {
            if let Ok(mut lock) = DECK_SESSION.lock() {
                *lock = Some(deckcraft_mcp::headless::Headless::default());
            }
        }
        _ => {}
    }
    create_jstring(&mut env, "{\"status\":\"ok\"}")
}

#[no_mangle]
pub extern "system" fn Java_com_storytold_craftsuite_core_enginebridge_NativeEngineBridge_nativeInspectDocument(
    mut env: JNIEnv,
    _class: JClass,
    engine_type: JString,
) -> jstring {
    let engine = safe_string(&mut env, &engine_type);
    let result = match engine.as_str() {
        "word" => execute_word_tool("inspect_document", &json!({})),
        "grid" => execute_grid_tool("inspect_workbook", &json!({})),
        "deck" => execute_deck_tool("inspect_document", &json!({})),
        _ => json!({"error": "unknown engine"}),
    };
    let s = serde_json::to_string(&result).unwrap_or_else(|_| "{}".to_string());
    create_jstring(&mut env, &s)
}

fn execute_word_tool(tool: &str, args: &Value) -> Value {
    let mut lock = match WORD_SESSION.lock() {
        Ok(guard) => guard,
        Err(poisoned) => poisoned.into_inner(),
    };
    let session = lock.get_or_insert_with(wordcraft_mcp::Headless::default);
    let tool_res = wordcraft_mcp::tools::call_tool(session, tool, args);
    tool_res.to_value()
}

fn execute_grid_tool(tool: &str, args: &Value) -> Value {
    let mut lock = match GRID_SESSION.lock() {
        Ok(guard) => guard,
        Err(poisoned) => poisoned.into_inner(),
    };
    let session = lock.get_or_insert_with(gridcraft_mcp::headless::Headless::new);
    let tool_res = gridcraft_mcp::tools::call_tool(session, tool, args);
    tool_res.to_value()
}

fn execute_deck_tool(tool: &str, args: &Value) -> Value {
    let mut lock = match DECK_SESSION.lock() {
        Ok(guard) => guard,
        Err(poisoned) => poisoned.into_inner(),
    };
    let session = lock.get_or_insert_with(deckcraft_mcp::headless::Headless::default);
    let tool_res = deckcraft_mcp::tools::call_tool(session, tool, args);
    tool_res.to_value()
}
