import { useState } from 'react'
import DateTimePicker, { DateTimePickerAndroid } from '@react-native-community/datetimepicker'
import { Platform, Pressable, Text, View } from 'react-native'
import { styles } from './formStyles'

function parseDateTime(value: string) {
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? new Date() : parsed
}

function formatDateTime(value: Date) {
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  const hours = String(value.getHours()).padStart(2, '0')
  const minutes = String(value.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day}T${hours}:${minutes}`
}

export default function DateTimePickerField({
  label,
  value,
  onChange,
}: {
  label: string
  value: string
  onChange: (value: string) => void
}) {
  const [selectedDate, setSelectedDate] = useState(() => parseDateTime(value))

  function openPicker() {
    DateTimePickerAndroid.open({
      value: selectedDate,
      mode: 'date',
      display: 'calendar',
      onChange: (event, date) => {
        if (event.type !== 'set' || !date) return
        DateTimePickerAndroid.open({
          value: date,
          mode: 'time',
          display: 'clock',
          onChange: (timeEvent, time) => {
            if (timeEvent.type !== 'set' || !time) return
            const combined = new Date(date)
            combined.setHours(time.getHours(), time.getMinutes(), 0, 0)
            setSelectedDate(combined)
            onChange(formatDateTime(combined))
          },
        })
      },
    })
  }

  function changeDateTime(_: unknown, date?: Date) {
    if (!date) return
    setSelectedDate(date)
    onChange(formatDateTime(date))
  }

  return (
    <View style={styles.field}>
      <Text style={styles.fieldLabel}>{label}</Text>
      {Platform.OS === 'ios' ? (
        <DateTimePicker
          value={selectedDate}
          mode="datetime"
          display="compact"
          onChange={changeDateTime}
          accentColor="#f59e0b"
        />
      ) : (
        <Pressable style={styles.dateButton} onPress={openPicker}>
          <Text style={styles.dateButtonText}>{selectedDate.toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' })}</Text>
          <Text style={styles.dateButtonIcon}>▦</Text>
        </Pressable>
      )}
    </View>
  )
}
