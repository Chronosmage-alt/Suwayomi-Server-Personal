/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { I18nProvider } from '@lingui/react';
import { i18n } from '@lingui/core';
import {
    HistoricalLearningReason,
    type HistoricalLearningDiagnostic,
} from '@/lib/graphql/generated/graphql-base.types.ts';
import { AdaptiveMangaSchedule } from '@/features/manga/components/details/AdaptiveMangaSchedule.tsx';

const controls = vi.hoisted(() => ({
    confirmations: 0,
    provisional: 7 as number | null,
    diagnostic: null as HistoricalLearningDiagnostic | null,
}));
vi.mock('@/lib/requests/RequestManager.ts', () => ({ requestManager: { graphQLClient: { client: {} } } }));
vi.mock('@apollo/client/react', () => ({
    useQuery: () => ({
        data: {
            adaptiveMangaSchedule: {
                mode: 'SMART',
                canManage: true,
                reason: 'LEARNING',
                observations: 0,
                historicalDiagnostic: controls.diagnostic,
                provisionalIntervalDays: controls.provisional,
                historicalConfirmations: controls.confirmations,
            },
        },
    }),
    useMutation: () => [vi.fn(), { loading: false }],
}));
vi.mock('@/base/utils/Toast.ts', () => ({ makeToast: vi.fn() }));
vi.mock('@/lib/HelperFunctions.ts', () => ({ getErrorMessage: vi.fn() }));
i18n.load('en', {});
i18n.activate('en');
afterEach(() => {
    cleanup();
    controls.confirmations = 0;
    controls.provisional = 7;
    controls.diagnostic = null;
});
const show = () =>
    render(
        <I18nProvider i18n={i18n}>
            <AdaptiveMangaSchedule mangaId={1} />
        </I18nProvider>,
    );
describe('historical release learning', () => {
    it('shows provisional progress and allows resetting before any new release', () => {
        controls.provisional = 7;
        for (const confirmations of [0, 1]) {
            controls.confirmations = confirmations;
            show();
            expect(screen.getByText(new RegExp(`${confirmations} of 2 new releases confirmed`)).textContent).toContain(
                'normal automatic updates continue',
            );
            expect(screen.getByText(/Provisional release interval/).textContent).toContain('7 days');
            expect(screen.getByText(/Recent qualifying release observations: 0/)).toBeTruthy();
            expect((screen.getByRole('button', { name: 'Reset release learning' }) as HTMLButtonElement).disabled).toBe(
                false,
            );
            cleanup();
        }
    });
    it('shows four-observation fallback when no reliable historical candidate exists', () => {
        controls.provisional = null;
        show();
        expect(screen.getByText(/Four consistent observations are required/)).toBeTruthy();
        expect(screen.queryByText(/Provisional release interval/)).toBeNull();
    });
    it('does not infer a historical assessment for legacy state', () => {
        controls.provisional = null;
        show();
        expect(screen.getByText(/has not been assessed since this update/)).toBeTruthy();
        expect(screen.queryByText(/Last historical assessment/)).toBeNull();
    });
    it.each([
        [HistoricalLearningReason.MissingDates, /recent chapters have missing dates/],
        [HistoricalLearningReason.UnknownDates, /origin of recent chapter dates is unknown/],
        [HistoricalLearningReason.FallbackDates, /server-generated fallback timestamps/],
        [HistoricalLearningReason.InconsistentIntervals, /recent release intervals are inconsistent/],
        [HistoricalLearningReason.StaleHistory, /latest historical release event is too old/],
        [HistoricalLearningReason.Suppressed, /suppressed after a reset or chapter restoration/],
        [HistoricalLearningReason.WaitingForNewHistory, /unchanged history cannot be retried/],
    ])('explains %s independently of fresh observations', (reason, message) => {
        controls.provisional = null;
        controls.diagnostic = {
            reason,
            assessedAt: '1792256762000',
            inspectedNumbers: 6,
            events: 0,
            missingDates: 1,
            unknownDates: 2,
            fallbackDates: 3,
            eligibleDates: 0,
        };
        show();
        expect(screen.getByText(message)).toBeTruthy();
        expect(screen.getByText(/Learning: 0 recent qualifying releases/)).toBeTruthy();
        expect(screen.getByText(/Inspected chapter numbers: 6/).textContent).toContain(
            '2 unknown, 3 fallback, 1 missing',
        );
        expect(screen.queryByText(/has not been assessed since this update/)).toBeNull();
    });
});
