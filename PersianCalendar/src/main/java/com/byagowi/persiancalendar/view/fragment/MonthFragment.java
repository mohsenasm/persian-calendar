package com.byagowi.persiancalendar.view.fragment;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.byagowi.persiancalendar.Constants;
import com.byagowi.persiancalendar.R;
import com.byagowi.persiancalendar.Utils;
import com.byagowi.persiancalendar.adapter.MonthAdapter;
import com.byagowi.persiancalendar.entity.Day;

import java.util.ArrayList;
import java.util.List;

import calendar.DateConverter;
import calendar.DayOutOfRangeException;
import calendar.PersianDate;

public class MonthFragment extends Fragment implements View.OnClickListener {
    private Utils utils;
    private CalendarMainFragment calendarMainFragment;
    private PersianDate persianDate;
    private char[] digits;
    private int offset;
    private IntentFilter filter;
    private BroadcastReceiver receiver;
    private MonthAdapter adapter;

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState) {

        utils = Utils.getInstance(getContext());
        View view = inflater.inflate(R.layout.fragment_month, container, false);
        offset = getArguments().getInt(Constants.OFFSET_ARGUMENT);
        List<Day> days = getDays(offset);
        digits = utils.preferredDigits();

        AppCompatImageView prev = (AppCompatImageView) view.findViewById(R.id.prev);
        AppCompatImageView next = (AppCompatImageView) view.findViewById(R.id.next);
        prev.setOnClickListener(this);
        next.setOnClickListener(this);

        persianDate = utils.getToday();
        int month = persianDate.getMonth() - offset;
        month -= 1;
        int year = persianDate.getYear();

        year = year + (month / 12);
        month = month % 12;
        if (month < 0) {
            year -= 1;
            month += 12;
        }

        month += 1;
        persianDate.setMonth(month);
        persianDate.setYear(year);
        persianDate.setDayOfMonth(1);

        RecyclerView recyclerView = (RecyclerView) view.findViewById(R.id.RecyclerView);
        recyclerView.setHasFixedSize(true);

        RecyclerView.LayoutManager layoutManager = new GridLayoutManager(getContext(), 7);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new MonthAdapter(getActivity(), this, days);
        recyclerView.setAdapter(adapter);

        calendarMainFragment = (CalendarMainFragment) getActivity()
                        .getSupportFragmentManager()
                        .findFragmentByTag(Constants.CALENDAR_MAIN_FRAGMENT_TAG);

        if (calendarMainFragment != null
                && offset == 0
                && CalendarMainFragment.viewPagerPosition == offset) {

            calendarMainFragment.selectDay(utils.getToday());
        }

        if (offset == 0 && CalendarMainFragment.viewPagerPosition == offset) {
            UpdateTitle();
        }

        filter = new IntentFilter(Constants.BROADCAST_INTENT_TO_MONTH_FRAGMENT);
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int value = intent.getExtras().getInt(Constants.BROADCAST_FIELD_TO_MONTH_FRAGMENT);
                if (value == offset) {
                    UpdateTitle();
                } else if (value == Constants.BROADCAST_TO_MONTH_FRAGMENT_RESET_DAY) {
                    resetSelectDay();
                }
            }
        };

        return view;
    }

    public void onClickItem(PersianDate day) {
        calendarMainFragment.selectDay(day);
    }

    public void onLongClickItem(PersianDate day) {
        calendarMainFragment.addEventOnCalendar(day);
    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {
            case R.id.next:
                calendarMainFragment.changeMonth(1);
                break;

            case R.id.prev:
                calendarMainFragment.changeMonth(-1);
                break;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getContext().registerReceiver(receiver, filter);
    }

    @Override
    public void onPause() {
        super.onPause();
        getContext().unregisterReceiver(receiver);
    }

    private void UpdateTitle() {
        utils.setActivityTitleAndSubtitle(
                getActivity(),
                utils.getMonthName(persianDate),
                utils.formatNumber(persianDate.getYear(), digits));
    }

    private void resetSelectDay() {
        if (adapter.select_Day != -1) {
            adapter.select_Day = -1;
            adapter.notifyDataSetChanged();
        }
    }

    private List<Day> getDays(int offset) {
        List<Day> days = new ArrayList<>();
        PersianDate persianDate = utils.getToday();
        int month = persianDate.getMonth() - offset;
        month -= 1;
        int year = persianDate.getYear();

        year = year + (month / 12);
        month = month % 12;
        if (month < 0) {
            year -= 1;
            month += 12;
        }
        month += 1;
        persianDate.setMonth(month);
        persianDate.setYear(year);
        persianDate.setDayOfMonth(1);

        char[] digits = utils.preferredDigits();

        int dayOfWeek = DateConverter.persianToCivil(persianDate)
                .getDayOfWeek() % 7;

        try {
            PersianDate today = utils.getToday();
            for (int i = 1; i <= 31; i++) {
                persianDate.setDayOfMonth(i);

                Day day = new Day();
                day.setNum(utils.formatNumber(i, digits));
                day.setDayOfWeek(dayOfWeek);

                String holidayTitle = utils.getHolidayTitle(persianDate);
                if (holidayTitle != null || dayOfWeek == 6) {
                    day.setHoliday(true);
                }

                String eventTitle = utils.getEventTitle(persianDate);
                if (!TextUtils.isEmpty(eventTitle) || holidayTitle != null ) {
                    day.setEvent(true);
                }

                day.setPersianDate(persianDate.clone());

                if (persianDate.equals(today)) {
                    day.setToday(true);
                }

                days.add(day);
                dayOfWeek++;
                if (dayOfWeek == 7) {
                    dayOfWeek = 0;
                }
            }
        } catch (DayOutOfRangeException e) {
            // okay, it was expected
        }

        return days;
    }
}
