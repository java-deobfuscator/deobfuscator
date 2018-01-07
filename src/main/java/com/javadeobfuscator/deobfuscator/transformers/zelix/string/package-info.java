/*
 * Copyright 2018 Sam Sun <github-contact@samczsun.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * This is a new approach at dealing with ZKM string encryption, since there are so many modes.
 *
 * Auto-detect is now responsible for determining which transformer to use. Each transformer will be written based on
 * one specific sample. No transformers should be modified after being written
 */
package com.javadeobfuscator.deobfuscator.transformers.zelix.string;