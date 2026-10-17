/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import path from 'node:path';
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react-swc';

export default defineConfig({
    plugins: [react({ plugins: [['@lingui/swc-plugin', {}]] })],
    resolve: { alias: { '@': path.resolve(import.meta.dirname, './src') } },
    test: {
        environment: 'jsdom',
        include: ['src/**/*.test.tsx'],
        restoreMocks: true,
    },
});
