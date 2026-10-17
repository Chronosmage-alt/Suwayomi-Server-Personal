/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { gql } from '@apollo/client';

export const COMPLETED_CATEGORY_SETTINGS = gql`
    query CompletedCategorySettings {
        completedCategorySettings {
            enabled
            categoryId
            effective
            destinationMissing
            categories {
                id
                name
            }
        }
    }
`;
export const SAVE_COMPLETED_CATEGORY_SETTINGS = gql`
    mutation SaveCompletedCategorySettings($enabled: Boolean!, $categoryId: Int) {
        setCompletedCategorySettings(enabled: $enabled, categoryId: $categoryId) {
            enabled
            categoryId
            effective
            destinationMissing
            categories {
                id
                name
            }
        }
    }
`;
export const COMPLETED_CATEGORY_PREVIEW = gql`
    query CompletedCategoryPreview($categoryId: Int!) {
        completedCategoryPreview(categoryId: $categoryId) {
            categoryId
            toAdd
            alreadyPresent
            skipped
        }
    }
`;
export const ORGANISE_COMPLETED_MANGA = gql`
    mutation OrganiseCompletedManga($categoryId: Int!) {
        organiseCompletedManga(categoryId: $categoryId) {
            categoryId
            toAdd
            alreadyPresent
            skipped
        }
    }
`;
