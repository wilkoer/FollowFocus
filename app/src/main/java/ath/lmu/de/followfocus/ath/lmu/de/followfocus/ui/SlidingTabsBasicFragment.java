/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ath.lmu.de.followfocus.ath.lmu.de.followfocus.ui;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import ath.lmu.de.followfocus.R;


public class SlidingTabsBasicFragment extends Fragment {

    private SlidingTabLayout mSlidingTabLayout;

    private ViewPager mViewPager;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.tab_fragment, container, false);
    }


    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {

        mViewPager = (ViewPager) view.findViewById(R.id.viewpager);
        mViewPager.setAdapter(new SamplePagerAdapter());

        mSlidingTabLayout = (SlidingTabLayout) view.findViewById(R.id.sliding_tabs);
        mSlidingTabLayout.setViewPager(mViewPager);
    }


    class SamplePagerAdapter extends PagerAdapter {

        // 3 pages - range calibration, realtime recording, in-out linear
        @Override
        public int getCount() {
            return 3;
        }



        @Override
        public boolean isViewFromObject(View view, Object o) {
            return o == view;
        }


        @Override
        public CharSequence getPageTitle(int position) {
            return "Item " + (position + 1);
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {

            View view = null;

            switch (position) {
                case 0:
                    view = getActivity().getLayoutInflater().inflate(R.layout.range_calibration_fragment,
                            container, false);
                    break;
                case 1:
                    view = getActivity().getLayoutInflater().inflate(R.layout.realtime_record_fragment,
                            container, false);
                    break;
                case 2:
                    view = getActivity().getLayoutInflater().inflate(R.layout.io_linear_fragment,
                            container, false);
                    break;
            }

            // Add the newly created View to the ViewPager
            container.addView(view);


            // Return the View
            return view;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
        }

    }
}
