import { useState } from 'react'
import DateTimePicker, { DateTimePickerAndroid } from '@react-native-community/datetimepicker'
import { Platform, Pressable, Text, View } from 'react-native'
import { styles } from './formStyles'

function parseDate(value: string) {
  const [year, month, day] = value.split('-').map(Number)
  return new Date(year!, month! - 1, day!)
}

function formatDate(value: Date) {
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function displayDate(value: string) {
  return parseDate(value).toLocaleDateString([], { year: 'numeric', month: 'short', day: 'numeric' })
}

export default function DatePickerField({
  label,
  value,
  onChange,
}: {
  label: string
  value: string
  onChange: (value: string) => void
}) {
  const [selectedDate, setSelectedDate] = useState(() => parseDate(value))

  function openPicker() {
    DateTimePickerAndroid.open({
      value: selectedDate,
      mode: 'date',
      display: 'calendar',
      onChange: (event, date) => {
        if (event.type === 'set' && date) {
          setSelectedDate(date)
          onChange(formatDate(date))
        }
      },
    })
  }

  function changeDate(_: unknown, date?: Date) {
    if (date) {
      setSelectedDate(date)
      onChange(formatDate(date))
    }
  }

  return (
    <View style={styles.field}>
      <Text style={styles.fieldLabel}>{label}</Text>
      {Platform.OS === 'ios' ? (
        <DateTimePicker
          value={selectedDate}
          mode="date"
          display="compact"
          onChange={changeDate}
          accentColor="#f59e0b"
        />
      ) : (
        <Pressable style={styles.dateButton} onPress={openPicker}>
          <Text style={styles.dateButtonText}>{displayDate(value)}</Text>
          <Text style={styles.dateButtonIcon}>▦</Text>
        </Pressable>
      )}
    </View>
  )
}
