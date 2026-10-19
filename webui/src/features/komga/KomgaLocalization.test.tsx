/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { describe, expect, it } from 'vitest';
import { setupI18n } from '@lingui/core';
import { msg } from '@lingui/core/macro';
import { messages } from '@/i18n/locales/en.po';

// Production omits macro fallback messages: the compiled catalogue must contain
// these IDs, even though development-mode component tests can look correct.
describe('compiled Komga English catalogue', () => {
    it.each([
        [msg`Automatically link matching Komga series`, 'Automatically link matching Komga series'],
        [msg`Show blue Komga book counter`, 'Show blue Komga book counter'],
        [msg`Scan for matching Komga series`, 'Scan for matching Komga series'],
        [msg`Manually confirmed link`, 'Manually confirmed link'],
        [msg`Confirm automatic link`, 'Confirm automatic link'],
        [msg`Komga link requires review`, 'Komga link requires review'],
    ])('contains the production message for $1', (descriptor, expected) => {
        const localization = setupI18n({ locale: 'en', messages: { en: messages } });
        expect(messages[descriptor.id]).toBeDefined();
        expect(localization._(descriptor.id)).toBe(expected);
    });
});
